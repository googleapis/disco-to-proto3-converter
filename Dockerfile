# This Dockerfile was used to build gcr.io/gapic-images/googleapis-bazel
# which is used in GitHub Actions CI for this repository
# (see .github/workflows/ci.yaml).

FROM python:3.8

RUN apt-get update
RUN apt install -y \
    zip \
    build-essential \
    python3-dev \
    openjdk-17-jdk

# Installing bazel as per https://docs.bazel.build/versions/3.7.0/install-ubuntu.html
RUN curl -fsSL https://bazel.build/bazel-release.pub.gpg | gpg --dearmor > bazel.gpg
RUN mv bazel.gpg /etc/apt/trusted.gpg.d/
RUN echo "deb [arch=amd64] https://storage.googleapis.com/bazel-apt stable jdk1.8" | tee /etc/apt/sources.list.d/bazel.list
RUN apt update && apt install -y bazel

ENTRYPOINT [ "/bin/bash" ]
