#!/usr/bin/env bash

MSYS_NO_PATHCONV=1 docker run --rm -w="/usr/local/tomcat" -v $(pwd):/usr/local/tomcat webratio/ant:1.10.1 ant compile
