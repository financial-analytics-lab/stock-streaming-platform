#!/usr/bin/env python3
"""Generate Parquet shards from EGX30 CSV for publisher-service testing."""

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
import json
import os
from pathlib import Path

# Paths
CSV_PATH = "/home/abdallah/Desktop/Grad Project/new pub sub/TRADES_20250101_20251231_EGX30_AUG.csv"
MAPPING_PATH = "/home/abdallah/Desktop/Grad Project/new pub sub/security_mapping.json"
OUTPUT_DIR = "/home/abdallah/Desktop/Grad Project/new pub sub/publisher-service/data/shards"

# Load security mapping
with open(MAPPING_PATH, 'r') as f:
    security_mapping = json.load(f)
print(f"Loaded {len(security_mapping)} security mappings")

# Read sample from CSV (first 500K rows for testing)
print(f"Reading CSV sample...")
df = pd.read_csv(
    CSV_PATH,
    sep=';',
    header=None,
    names=['date', 'security_code', 'type', 'transaction_id', 'price', 'volume', 'open', 'close', 'datetime'],
    nrows=500000,  # Limit for quick testing
    dtype={'security_code': str}
)
print(f"Loaded {len(df):,} rows")

# Parse datetime (format: dd/MM/yyyy HH:mm:ss a)
df['datetime_parsed'] = pd.to_datetime(df['datetime'], format='%d/%m/%Y %I:%M:%S %p', dayfirst=True)
# Parse session_date as YYYYMMDD from dd/MM/yyyy format
df['session_date'] = pd.to_datetime(df['date'], dayfirst=True).dt.strftime('%Y%m%d')

# Map security codes to names
df['security_name'] = df['security_code'].map(security_mapping).fillna(df['security_code'])

# Create sequence_id per session_date
df = df.sort_values(['session_date', 'datetime_parsed'])
df['sequence_id'] = df.groupby('session_date').cumcount() + 1

# Create session_id
df['session_id'] = df['session_date'] + '_EGX'

# Preview
print("\nSample data:")
print(df[['date', 'security_code', 'security_name', 'price', 'volume', 'datetime', 'session_date']].head(10))
print(f"\nUnique sessions: {df['session_date'].nunique()}")
print(f"Sessions: {sorted(df['session_date'].unique())}")

# Write Parquet shards by session_date
print(f"\nWriting Parquet shards to {OUTPUT_DIR}...")
os.makedirs(OUTPUT_DIR, exist_ok=True)

for session_date, group in df.groupby('session_date'):
    shard_dir = os.path.join(OUTPUT_DIR, f"session_date={session_date}")
    os.makedirs(shard_dir, exist_ok=True)

    # Convert to PyArrow table
    table = pa.Table.from_pandas(group)

    # Write to parquet
    output_path = os.path.join(shard_dir, f"part-00000.parquet")
    pq.write_table(table, output_path, row_group_size=50000)

    print(f"  Wrote {len(group):,} events for session {session_date} -> {output_path}")

print("\nDone! Parquet shards created successfully.")