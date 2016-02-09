#!/usr/bin/env bash

sbt assembly
MAINJAR="assembly.jar"

if [ "$?" -eq "0" ]
then
  ONEJAR=`find ./target -name *assembly-0.0.1.jar`
  echo "Moving Jar $ONEJAR to $MAINJAR"
  mv $ONEJAR $MAINJAR
else
  exit $?
fi
