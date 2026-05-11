# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a graduate project repository for a publisher/subscriber trading system. It contains market data and system specifications rather than a traditional codebase.

## Contents

- **TRADES_20250101_20251231_EGX30_AUG.csv** - Historical trade data from EGX (Egyptian Exchange) covering EGX30 index/ETF trades from January 2025 to December 2025
- **publisher_service_refined_spec_latex.pdf** - System specification document for the publisher service

## Data Format

The CSV file contains trade data with columns for timestamp, instrument identifier, price, volume, and other market data fields.

## No Build/Test Commands

This repository does not contain executable code. There are no build, lint, or test commands to run.

## Working with the Data

The CSV file can be analyzed using standard data tools (pandas, pandas-profiling, etc.). Given its size (~780MB), consider:
- Loading with chunked processing for large-scale analysis
- Using duckdb or polars for memory-efficient operations