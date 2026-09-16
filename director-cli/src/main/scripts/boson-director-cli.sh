#!/bin/sh
#
# Runs boson-director-cli from a directory holding bin/ and lib/: a Boson distribution, or target/dist.
#
# Java is the bundled JRE when there is one, then $JAVA_HOME, then java on the PATH. JAVA_OPTS is
# passed to it.

# Resolve symbolic links: packages link this script into /usr/bin.
PRG="$0"
while [ -h "$PRG" ]; do
  link=$(ls -ld "$PRG" | sed 's/.*-> //')
  case "$link" in
    /*) PRG="$link" ;;
    *) PRG="$(dirname "$PRG")/$link" ;;
  esac
done
BASEDIR=$(cd "$(dirname "$PRG")/.." > /dev/null && pwd)

if [ -x "$BASEDIR/jre/bin/java" ]; then
  JAVA="$BASEDIR/jre/bin/java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

# The serial collector: a command runs briefly and needs no parallel GC threads to start.
exec "$JAVA" -XX:+UseSerialGC $JAVA_OPTS -cp "$BASEDIR/lib/*" io.bosonnetwork.director.cli.BosonDirectorCli "$@"
