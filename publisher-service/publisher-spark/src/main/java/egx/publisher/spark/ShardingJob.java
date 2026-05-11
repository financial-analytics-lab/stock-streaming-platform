package egx.publisher.spark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import static org.apache.spark.sql.functions.array;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.date_format;
import static org.apache.spark.sql.functions.explode;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.map_entries;
import static org.apache.spark.sql.functions.map_from_arrays;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

public class ShardingJob {

    // Docker paths, not host-machine paths.
    private static final String DEFAULT_INPUT = "/data/TRADES_20250101_20251231_EGX30_AUG.csv";
    private static final String DEFAULT_OUTPUT = "/data/shards_csv";
    private static final String DEFAULT_MAPPING = "/data/security_mapping.json";

    public static void main(String[] args) {
        String inputPath = getArgValue(args, "input", DEFAULT_INPUT);
        String outputPath = getArgValue(args, "output", DEFAULT_OUTPUT);
        String mappingPath = getArgValue(args, "mapping", DEFAULT_MAPPING);

        SparkSession spark = SparkSession.builder()
                .appName("EGXPublisher-ShardingJob")
                .config("spark.sql.shuffle.partitions", "16")
                .config("spark.sql.legacy.timeParserPolicy", "LEGACY")
                .getOrCreate();

        try {
            System.out.println("[ShardingJob] Starting job");
            System.out.println("[ShardingJob] Input path   : " + inputPath);
            System.out.println("[ShardingJob] Output path  : " + outputPath);
            System.out.println("[ShardingJob] Mapping path : " + mappingPath);

            StructType schema = new StructType()
                    .add("date", DataTypes.StringType, true)
                    .add("security_code", DataTypes.StringType, true)
                    .add("type", DataTypes.StringType, true)
                    .add("transaction_id", DataTypes.StringType, true)
                    .add("price", DataTypes.DoubleType, true)
                    .add("volume", DataTypes.IntegerType, true)
                    .add("open", DataTypes.DoubleType, true)
                    .add("close", DataTypes.DoubleType, true)
                    .add("datetime", DataTypes.StringType, true);

            Dataset<Row> rawDF = spark.read()
                    .option("header", false)
                    .option("delimiter", ";")
                    .option("mode", "PERMISSIVE")
                    .schema(schema)
                    .csv(inputPath);

            long rawCount = rawDF.count();
            System.out.println("[ShardingJob] Loaded rows from CSV: " + rawCount);

            if (rawCount == 0) {
                System.out.println("[ShardingJob] No rows found. Stopping job.");
                return;
            }

            Dataset<Row> mappingRawDF = spark.read()
                    .option("multiline", true)
                    .json(mappingPath);

            Dataset<Row> mappingDF = normalizeMapping(mappingRawDF).cache();

            Dataset<Row> mappingSelectedDF = mappingDF.select(
                    col("security_code").alias("mapping_security_code"),
                    col("symbol_code"),
                    col("security_name")
            );

            Dataset<Row> enrichedDF = rawDF.join(
                    mappingSelectedDF,
                    rawDF.col("security_code").equalTo(mappingSelectedDF.col("mapping_security_code")),
                    "left"
            ).drop("mapping_security_code");

            Dataset<Row> withDatetime = enrichedDF
                    .withColumn(
                            "event_time_original",
                            coalesce(
                                    expr("try_to_timestamp(datetime, 'dd/MM/yyyy hh:mm:ss a')"),
                                    expr("try_to_timestamp(datetime, 'dd/MM/yyyy HH:mm:ss')"),
                                    expr("try_to_timestamp(datetime, 'dd/MM/yyyy')")
                            )
                    )
                    .withColumn(
                            "parsed_date_from_date_column",
                            expr("try_to_timestamp(date, 'dd/MM/yyyy')")
                    )
                    .withColumn(
                            "session_date",
                            coalesce(
                                    date_format(col("parsed_date_from_date_column"), "yyyyMMdd"),
                                    date_format(col("event_time_original"), "yyyyMMdd")
                            )
                    );

            long invalidRows = withDatetime
                    .filter(
                            col("event_time_original").isNull()
                                    .or(col("session_date").isNull())
                                    .or(col("security_code").isNull())
                                    .or(col("transaction_id").isNull())
                    )
                    .count();

            if (invalidRows > 0) {
                System.out.println("[ShardingJob] Skipping invalid rows: " + invalidRows);
            }

            Dataset<Row> cleanedDF = withDatetime
                    .filter(
                            col("event_time_original").isNotNull()
                                    .and(col("session_date").isNotNull())
                                    .and(col("security_code").isNotNull())
                                    .and(col("transaction_id").isNotNull())
                    );

            long cleanedCount = cleanedDF.count();
            System.out.println("[ShardingJob] Valid rows after cleaning: " + cleanedCount);

            if (cleanedCount == 0) {
                System.out.println("[ShardingJob] No valid rows after cleaning. Stopping job.");
                return;
            }

            org.apache.spark.sql.expressions.WindowSpec windowSpec = Window
                    .partitionBy(col("session_date"))
                    .orderBy(col("event_time_original").asc(), col("transaction_id").asc());

            Dataset<Row> finalDF = cleanedDF
                    .withColumn("session_id", concat(col("session_date"), lit("_EGX")))
                    .withColumn("sequence_id", functions.row_number().over(windowSpec))
                    .select(
                            col("event_time_original"),
                            coalesce(col("symbol_code"), col("security_code")).alias("symbol_code"),
                            coalesce(col("security_name"), col("security_code")).alias("security_name"),
                            col("session_id"),
                            col("session_date"),
                            col("sequence_id"),
                            col("transaction_id"),
                            col("price"),
                            col("volume"),
                            col("open"),
                            col("close"),
                            col("type")
                    )
                    .cache();

            long finalCount = finalDF.count();
            System.out.println("[ShardingJob] Processed rows with canonical columns: " + finalCount);

            System.out.println("[ShardingJob] Preview:");
            finalDF.show(20, false);

            Dataset<Row> outputDF = finalDF.repartition(16, col("session_date"));

            outputDF.write()
                    .mode(org.apache.spark.sql.SaveMode.Overwrite)
                    .option("header", "true")
                    .option("delimiter", ",")
                    .option("quote", "\"")
                    .option("escape", "\"")
                    .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
                    .partitionBy("session_date")
                    .csv(outputPath);

            System.out.println("[ShardingJob] Successfully wrote CSV output to: " + outputPath);

            long sessionCount = finalDF.select("session_date").distinct().count();
            System.out.println("[ShardingJob] Total sessions written: " + sessionCount);

            long symbolCount = finalDF.select("symbol_code").distinct().count();
            System.out.println("[ShardingJob] Total unique symbols: " + symbolCount);

        } catch (Exception e) {
            System.err.println("[ShardingJob] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            spark.stop();
            System.out.println("[ShardingJob] Spark session stopped");
        }
    }

    private static String getArgValue(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--" + key)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static Dataset<Row> normalizeMapping(Dataset<Row> mappingRawDF) {
        String[] cols = mappingRawDF.columns();
        java.util.List<String> colList = java.util.Arrays.asList(cols);

        /*
         * Supports row-based JSON like:
         * [
         *   {
         *     "security_code": "COMI",
         *     "symbol_code": "COMI",
         *     "security_name": "Commercial International Bank"
         *   }
         * ]
         */
        if (colList.contains("security_code") && colList.contains("security_name")) {
            if (colList.contains("symbol_code")) {
                return mappingRawDF.select("security_code", "symbol_code", "security_name");
            }

            return mappingRawDF
                    .withColumn("symbol_code", col("security_code"))
                    .select("security_code", "symbol_code", "security_name");
        }

        /*
         * Supports object-based JSON like:
         * {
         *   "COMI": "Commercial International Bank",
         *   "HRHO": "EFG Holding"
         * }
         */
        Column[] keyColumns = new Column[cols.length];
        Column[] valueColumns = new Column[cols.length];

        for (int i = 0; i < cols.length; i++) {
            keyColumns[i] = lit(cols[i]);
            valueColumns[i] = col("`" + cols[i] + "`");
        }

        Dataset<Row> explodedDF = mappingRawDF
                .select(
                        explode(
                                map_entries(
                                        map_from_arrays(
                                                array(keyColumns),
                                                array(valueColumns)
                                        )
                                )
                        ).alias("entry")
                );

        DataType firstValueType = mappingRawDF.schema().fields()[0].dataType();

        /*
         * If object values are nested objects like:
         * {
         *   "COMI": {
         *     "symbol_code": "COMI",
         *     "security_name": "Commercial International Bank"
         *   }
         * }
         */
        if (firstValueType instanceof StructType) {
            return explodedDF.select(
                    col("entry.key").alias("security_code"),
                    coalesce(col("entry.value.symbol_code"), col("entry.key")).alias("symbol_code"),
                    coalesce(col("entry.value.security_name"), col("entry.key")).alias("security_name")
            );
        }

        return explodedDF
                .select(
                        col("entry.key").alias("security_code"),
                        col("entry.value").cast("string").alias("security_name")
                )
                .withColumn("symbol_code", col("security_code"))
                .select("security_code", "symbol_code", "security_name");
    }
}