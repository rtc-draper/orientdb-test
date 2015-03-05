#!/bin/bash

# Update repo directory with the latest libraries from /opt/orientdb/lib
# This is required when yuou recompile orientdb!

VERSION="2.0.4"
BASEDIR=../orientdb/distribution/target/
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

for f in $(find . -name "maven-metadata*"); do
    FILENAME=${f/-local/}
    cp $f $FILENAME
done
