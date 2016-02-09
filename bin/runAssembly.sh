#!/usr/bin/env bash

MAINJAR="assembly.jar"

CLASSES="scripts/classes"

mkdir -p $CLASSES

echo "Removing Classes"
rm $CLASSES/*.*

echo "Compiling Classes"
scalac -d $CLASSES -cp $MAINJAR scripts/*.scala


echo "Running Program"
scala -cp $CLASSES:$MAINJAR $*


