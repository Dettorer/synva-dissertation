#!/usr/bin/env sh
./collect_data.sh -m ../python3k/compressed/graph > collected.csv \
    && diff <(sort expected_python3k_collected.csv) <(sort collected.csv) \
    && echo -e "\e[32m[OK] the collected data matches the expected data\e[0m" \
    || echo -e "\e[31m[KO] error, or data collected is different from the expected one (see diff result above)\e[0m"
