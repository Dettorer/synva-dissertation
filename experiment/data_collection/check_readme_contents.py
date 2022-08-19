#!/usr/bin/env python3
import boto3
import botocore.exceptions
import csv
import gzip
import os
import re
import requests
import sys

from charset_normalizer import from_bytes as magic_decode
from more_itertools import windowed
from mypy_boto3_s3 import Client as S3Client
from typing import Dict, Optional, Tuple


SWH_ARCHIVE_RATE_LIMIT_REACHED = False
CONTENT_CACHE_DIR = "CACHED_READMES"
S3_CLIENT = None


def get_s3() -> S3Client:
    global S3_CLIENT
    if S3_CLIENT is None:
        S3_CLIENT = boto3.client("s3")
    return S3_CLIENT


def eprint(*args, **argv) -> None:
    print(*args, **argv, file=sys.stderr)


def usage() -> None:
    eprint(f"usage: {sys.argv[0]} <input csv file> <output csv file> [<sha1_git to sha1 csv file>]")
    eprint("for every hasContrib=CHECKREADMECONTENT line in the input csv file,")
    eprint("fetch the content of the readme through URL in the readmeUrl column.")
    eprint("Optionally, if a sha1_git to sha1 csv file is given, fetch the readme")
    eprint("contents through aws (s3://softwareheritage/content/) instead (falling")
    eprint("back on the readmeUrl URL for missing sha1s)")


def get_s3_content(sha1_git: str, sha1_assoc: Dict[str, str]) -> Optional[str]:
    """Try to fetch the content of the README file identified by `sha1_git` from
    software heritage's aws s3"""
    sha1 = sha1_assoc.get(sha1_git)
    try:
        if sha1 is None:
            return None

        eprint(f"{sha1_git}: querying s3... ", end="")
        s3_response = get_s3().get_object(
            Bucket="softwareheritage",
            Key=f"content/{sha1}"
        )
        raw = gzip.decompress(s3_response["Body"].read())
        content = str(magic_decode(raw).best())
        eprint("success")
        return content
    except botocore.exceptions.ClientError as e:
        if e.response['Error']['Code'] == 'NoSuchKey':
            eprint(f"fail: sha1 {sha1} was in the association CSV but was not found in s3")
            return None
        else:
            raise


CACHED_LIST = None

def get_cached_content(sha1_git: str) -> Tuple[bool, Optional[str]]:
    f"""Try to fetch the content of the file identified by `sha1_git` from the
    {CONTENT_CACHE_DIR} directory.

    The returned boolean is True if the file was indeed in the cache. If that
    boolean is True but the returned content is still None, then the cached data
    could not be decoded."""
    global CACHED_LIST

    if not os.path.isdir(CONTENT_CACHE_DIR):
        os.mkdir(CONTENT_CACHE_DIR)

    if CACHED_LIST is None:
        CACHED_LIST = os.listdir(CONTENT_CACHE_DIR)

    if sha1_git in CACHED_LIST:
        with open(os.path.join(CONTENT_CACHE_DIR, sha1_git), "rb") as readme:
            try:
                content = gzip.decompress(readme.read()).decode()
                eprint(f"{sha1_git}: fetched from cache")
                return True, content
            except UnicodeDecodeError:
                eprint(f"{sha1_git}: can't decode, skipping")
                return True, None

    return False, None


def set_cached_content(sha1_git: str, content: str) -> None:
    f"""Save the given content in a gzip-compressed file named `sha1_git` (which
    identifies the file) in the {CONTENT_CACHE_DIR} directory
    """
    global CACHED_LIST

    if not os.path.isdir(CONTENT_CACHE_DIR):
        os.mkdir(CONTENT_CACHE_DIR)
    if CACHED_LIST is None:
        CACHED_LIST = os.listdir(CONTENT_CACHE_DIR)

    with open(os.path.join(CONTENT_CACHE_DIR, sha1_git), "wb") as readme:
        readme.write(gzip.compress(content.encode()))
    CACHED_LIST += sha1_git


def get_readme_content(
    url: str,
    sha1_assoc: Optional[Dict[str, str]]
) -> Optional[str]:
    f"""Get the unicode-encoded content of the README file identified by the
    given softwareheritage archive url.

    If the `sha1_assoc` dictionnary is given, it is used to translate the
    sha1_git hash found in the url to a sha1 hash, which is then used to query
    the content from software heritage's aws s3 instead (which isn't rate
    limited). If the dictionnary isn't given, or doesn't contain a sha1 hash for
    the given sha1_git hash, or if the content could not otherwise be fetched
    from aws s3, this function directly queries the given url as a fallback
    (which is conservatively rate limited).

    Every README file successfully fetched is also cached in a gzip-compressed
    file in {CONTENT_CACHE_DIR} (which is automatically checked before trying
    any of the previously mentionned fetching method).
    """
    global SWH_ARCHIVE_RATE_LIMIT_REACHED
    # assuming urls of the form:
    # https://archive.softwareheritage.org/api/1/content/sha1_git:<sha1_git>/raw/
    sha1_git = url.split(':')[2].split('/')[0]

    # Check cached readmes
    was_cached, readme_content = get_cached_content(sha1_git)
    if was_cached and readme_content is None:
        # the cached file can't be decoded, ignore
        return None

    if readme_content is None and sha1_assoc is not None:
        # try querying S3
        readme_content = get_s3_content(sha1_git, sha1_assoc)
    if readme_content is None:
        if SWH_ARCHIVE_RATE_LIMIT_REACHED:
            eprint(f"{sha1_git}: skipping (archive.softwareheritage.org rate limit reached) ")
        else:
            # falling back on the HTTP archive (rate limited)
            eprint(f"{sha1_git}: querying archive.softwareheritage.org... ", end="")
            r = requests.get(url)
            if r.status_code == requests.codes.ok:
                eprint("success")
                readme_content = r.text
            else:
                eprint(f"failed with status code {r.status_code}")
                if r.status_code == 429:
                    SWH_ARCHIVE_RATE_LIMIT_REACHED = True
                readme_content = None

    if not was_cached and readme_content is not None:
        set_cached_content(sha1_git, readme_content)

    return readme_content


def is_header(line: str, next_line: Optional[str]) -> bool:
    return (
        line.startswith("#") # standard markdown header
        or (
            next_line is not None
            # alternate markdown header or standard reStructuredText header
            and bool(re.match(r"""[=\-`:'"~^_*+#<>]+\s*""", next_line))
        )
    )


# TODO: transform to regular expressions with word bounds check
CONTRIBUTING_VARIANTS = {
    "contributing",
    "contribution",
    "contribute",
    "contrib"
}

def readme_has_contrib(content: str) -> bool:
    """Check the content for a markdown or reStructuredText header that contains
    a "contributing"-related word"""
    for line, next_line in windowed(content.splitlines(), 2):
        if line is not None and is_header(line, next_line):
            # `line` is a md or rst header
            for variant in CONTRIBUTING_VARIANTS:
                if variant in line.lower():
                    return True

    return False


def process_csv(
    collected_data: csv.DictReader,
    completed_data: csv.DictWriter,
    row_count: int,
    sha1_assoc: Optional[Dict[str, str]] = None
) -> None:
    """Scan the `collected_data` CSV, checking the README of every
    "CHECKREADMECONTENT" for contribution guidelines, updating the `hasContrib`
    row accordingly.

    The updated data is written to `completed_data`, with already complete rows
    copied as-is.

    `row_count` allows the function to print the current percentage of scanned
    rows.

    The optional `sha1_assoc` dictionnary can be given to translate sha1_git
    hashes to sha1 hashes, which can then be queried through software heritage's
    aws s3, which isn't rate limited.
    """
    completed_data.writeheader()
    treated = 0
    for row in collected_data:
        treated += 1
        percentage = treated * 100 / row_count
        if row["hasContrib"] == "CHECKREADMECONTENT":
            eprint(f"{percentage:3.2f}% ", end="")
            readmeContent = get_readme_content(row["readmeUrl"], sha1_assoc)
            if readmeContent is not None:
                row["hasContrib"] = str(readme_has_contrib(readmeContent)).upper()
                row["readmeUrl"] = "checked"
            else:
                row["hasContrib"] = "INCONCLUSIVE"
                row["readmeUrl"] = "content unavailable"
        completed_data.writerow(row)


if __name__ == "__main__":
    if len(sys.argv) not in [3, 4]:
        usage()
    else:
        with open(sys.argv[1], "r") as input_file:
            project_count = sum(1 for _line in input_file)
            input_file.seek(0)
            with open(sys.argv[2], "w") as output_file:
                collected_data = csv.DictReader(input_file)
                if collected_data.fieldnames == None:
                    eprint(f"Missing CSV header in {sys.argv[1]}")
                    sys.exit(1)
                completed_data = csv.DictWriter(
                    output_file,
                    collected_data.fieldnames
                )
                if len(sys.argv) == 3:
                    process_csv(collected_data, completed_data, project_count)
                else:
                    with open(sys.argv[3], "r") as sha1_file:
                        # prepare the sha1_git to sha1 dictionnary
                        sha1_csv = csv.DictReader(sha1_file)
                        sha1_assoc: Dict[str, str] = {}
                        for row in sha1_csv:
                            sha1_assoc[row["sha1_git"]] = row["sha1"]
                        process_csv(collected_data, completed_data, project_count, sha1_assoc)
