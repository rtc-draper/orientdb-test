#!/bin/bash

# Update repo directory with the latest libraries from /opt/orientdb/lib
# This is required when yuou recompile orientdb!

VERSION="1.7.8-SNAPSHOT"
BASEDIR=/opt/orientdb/lib/
REPODIR=repo/
GROUPID="com.orientechnologies"

for f in $(find $BASEDIR -name "*-$VERSION.jar"); do
    FILENAME=${f##*/} 
    ARTIFACTID=${FILENAME%%"-$VERSION.jar"}
    
    mvn install:install-file \
  	-DlocalRepositoryPath=$REPODIR \
	-DcreateChecksum=true \
	-Dpackaging=jar \
	-Dfile="$f" \
	-DgroupId=$GROUPID \
	-DartifactId=$ARTIFACTID \
	-Dversion=$VERSION
done
