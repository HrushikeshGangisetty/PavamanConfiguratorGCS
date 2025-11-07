#!/bin/bash
# Download first 10KB of the JSON to examine structure
curl -s "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json" | head -c 10000 > sample.json
echo "First 10KB saved to sample.json"
cat sample.json

