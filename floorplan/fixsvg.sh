#!/bin/bash

echo $1
sed -i -re 's/stroke=[^/]+/fill="black"/g' "$1"
#sed -i -re 's/fill=[^/]+/fill="none" stroke="black" stroke-width="1"/' "$1"
sed -i -re 's/translate\([^\)]+\)//' "$1"

