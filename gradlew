#!/bin/sh

APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit
exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
