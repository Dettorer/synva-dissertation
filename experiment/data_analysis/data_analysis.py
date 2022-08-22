#!/usr/bin/env python3

import argparse
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pandas.api.types as pd_types

from scipy import stats
from typing import cast


def float_to_tex(f: float) -> str:
    """Represent the given float in LaTeX, handling the possibly needed scientific notation"""
    float_str = f"{f:.8g}"
    if "e" in float_str:
        base, exponent = float_str.split("e")
        return r"{0} \times 10^{{{1}}}".format(base, int(exponent))
    else:
        return float_str


def write_regression_viz(
    data: pd.DataFrame,
    x_col: str,
    y_col: str,
    scale: str = "linear",
) -> None:
    # Prepare the data
    sorted_data = cast(pd.DataFrame, data.sort_values(by=[x_col]))
    x, y = sorted_data[x_col], sorted_data[y_col]
    model = stats.linregress(x, y)

    # Plot
    plt.scatter(x, y, s=0.5)
    plt.plot(x, model.intercept + model.slope * x, 'r', label="regression")
    plt.xlabel(x_col)
    plt.ylabel(y_col)
    plt.xscale(scale)
    plt.yscale(scale)
    plt.legend()

    # Output and clean
    plt.savefig(f"{x_col}Regression_{scale}Scale.png")
    plt.clf()
    with open(f"{x_col}RegressionFormula.tex", "w") as outfile:
        tex_x = f"\\mathit{{{x_col}}}"
        tex_y = f"\\mathit{{{y_col}}}"
        tex_slope = f"{float_to_tex(model.slope)}"
        tex_intercept = f"{float_to_tex(abs(model.intercept))}"
        intercept_sign = "+" if model.intercept >= 0 else "-"
        tex_formula = f"{tex_y} = {tex_x} \\times {tex_slope} {intercept_sign} {tex_intercept}"
        outfile.write(f"${tex_formula}$\\\\($r^2 = {float_to_tex(model.rvalue)}$)")


def test_hasContrib(data: pd.DataFrame) -> None:
    # Output histograms
    data.groupby(["hasContrib"])["hasContrib"].count().plot.bar()
    plt.ylabel("projects")
    plt.xticks(rotation=0, horizontalalignment="center")
    plt.savefig("hasContrib_Count.png")
    plt.clf()

    data.groupby(["hasContrib"])["newContributorCount"].mean().plot.bar()
    plt.ylabel("newContributorCount (mean)")
    plt.xticks(rotation=0, horizontalalignment="center")
    plt.savefig("hasContrib_meanNewContributorCount.png")
    plt.clf()

    # Compute Mann-Whitney U test
    with_contrib = data[data["hasContrib"] == "yes"]
    without_contrib = data[data["hasContrib"] == "no"]

    u_stat, p_value = stats.mannwhitneyu(
        with_contrib["newContributorCount"],
        without_contrib["newContributorCount"],
        alternative="greater",  # we expect with_contrib to have more new contributors
    )

    with open("hasContribTest.tex", "w") as outfile:
        u, p = map(float_to_tex, (u_stat, p_value))
        ρ_effect_size = float_to_tex(u_stat / (len(with_contrib) * len(without_contrib)))
        outfile.write(f"Mann-Whitney statistic: $U = {u}$ ($p = {p}$, $ρ = {ρ_effect_size}$)")


def write_initial_viz(data: pd.DataFrame) -> None:
    """Output some initial visualizations"""
    for column_name in [
        "hasContrib",
        "newContributorCount",
        "recentContributorCount",
        "recentCommitCount"
    ]:
        # column descriptions, LaTeX format
        tex_output = data[[column_name]] \
            .describe() \
            .style.to_latex() \
            .replace("%", "\\%") # fix a formatting oversight
        with open(f"{column_name}_describe.tex", "w") as outfile:
            outfile.write(tex_output)

        # distribution
        column: pd.Series = data[column_name]
        if pd_types.is_numeric_dtype(column.dtype):
            column.plot.hist(bins=50)
            plt.yscale("log")
            plt.savefig(f"{column_name}_distribution.png")
            plt.clf()



def clean_data(data: pd.DataFrame) -> pd.DataFrame:
    """Clean the CSV data in place:

    - change the hasContrib column's values to "yes", "no" or pandas.NA
    """
    # remove all projects that have less than two recent contributors (exclusion
    # criterion)
    data = data[data["recentContributorCount"] >= 2]

    # rewrite the values of the hasContrib column to better integrate with
    # pandas and visualizations
    data = cast(
        pd.DataFrame,
        data.replace({
            "hasContrib":
            {
                "TRUE": "yes",
                "FALSE": "no",
                "CHECKREADMECONTENT": pd.NA,
                "INCONCLUSIVE": pd.NA
            }
        })
    )

    return data


def setup_argparse() -> argparse.ArgumentParser:
    """Set argparse arguments"""
    parser = argparse.ArgumentParser(description="""
        Analyze the given CSV and produce visualizations
    """)
    parser.add_argument(
        "data_csv",
        metavar="data.csv",
        type=str,
        help="the path of the CSV file containing data to be analyzed"
    )
    return parser


if __name__ == "__main__":
    args = setup_argparse().parse_args()

    print("Preparing the data...")
    initial_data = cast(
        pd.DataFrame,
        pd.read_csv(
            args.data_csv,
            # force pandas to keep the hasContrib column as a string (otherwise
            # it infers it as boolean but only for some rows, resulting in a
            # mixed type column), we clean that column in clean_data() anyway.
            dtype={"hasContrib": "str"}
        )
    )
    data = clean_data(initial_data)
    print("Computing the initial visualizations")
    write_initial_viz(data)

    # hasContrib analysis
    print("Computing hasContrib visualizations and statistics")
    test_hasContrib(data)

    # Recent contributor count and recent commit count analysis
    for x_col in ["recentContributorCount", "recentCommitCount"]:
        for scale in ["linear", "log"]:
            print(f"Computing regression for {x_col} ({scale} scale)")
            write_regression_viz(data, x_col, "newContributorCount", scale)
