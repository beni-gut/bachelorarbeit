#!/bin/bash

cd /home/andokai/bachelorarbeit/Qanary/qanary_pipeline-template/target/
java -jar /home/andokai/bachelorarbeit/Qanary/qanary_pipeline-template/target/qa.pipeline-2.4.0.jar --qanary.triplestore=http://admin:admin@localhost:5820/qanary
