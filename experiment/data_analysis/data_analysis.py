#!/usr/bin/env python3

import argparse
import matplotlib.pyplot as plt
import pandas as pd

from scipy import stats
from typing import cast


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
    plt.scatter(x, y)
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
        tex_slope = f"{model.slope:.8f}"
        tex_intercept = f"{model.intercept:.8f}"
        tex_formula = f"{tex_y} = {tex_x} * {tex_slope} + {tex_intercept}"
        outfile.write(f"${tex_formula}$\\\\($r^2 = {model.rvalue:.8f}$)")


def write_initial_viz(data: pd.DataFrame) -> None:
    """Output some initial visualizations"""
    # column descriptions, LaTeX format
    for column in [
        "hasContrib",
        "newContributorCount",
        "recentContributorCount",
        "recentCommitCount"
    ]:
        tex_output = data[[column]] \
            .describe() \
            .style.to_latex() \
            .replace("%", "\\%") # fix a formatting oversight
        with open(f"{column}_describe.tex", "w") as outfile:
            outfile.write(tex_output)

    # contribution guidelines presence histogram, SVG
    data.groupby(["hasContrib"])["hasContrib"].count().plot.bar()
    plt.ylabel("projects")
    plt.xticks(rotation=0, horizontalalignment="center")
    plt.savefig("hasContrib_Count.svg")
    plt.clf()

    data.groupby(["hasContrib"])["newContributorCount"].mean().plot.bar()
    plt.ylabel("newContributorCount (mean)")
    plt.xticks(rotation=0, horizontalalignment="center")
    plt.savefig("hasContrib_meanNewContributorCount.svg")
    plt.clf()


def clean_data(data: pd.DataFrame) -> pd.DataFrame:
    """Clean the CSV data in place:

    - change the hasContrib column's values to "with", "without" or pandas.NA
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
                "TRUE": "with",
                "FALSE": "without",
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
    write_initial_viz(data)
    for x_col in ["recentContributorCount", "recentCommitCount"]:
        for scale in ["linear", "log"]:
            write_regression_viz(data, x_col, "newContributorCount", scale)
