#!/usr/bin/env bash

# Stardog
# installing stardog
sudo curl http://packages.stardog.com/stardog.gpg.pub | sudo apt-key add
echo "deb http://packages.stardog.com/deb/ stable main" | sudo tee -a /etc/apt/sources.list
sudo apt-get update
sudo apt-get install -y stardog

# requesting a license
sudo su
export PATH="$PATH:/opt/stardog/bin"
export STARDOG_HOME=/var/opt/stardog
stardog-admin license request

# stardog controls, start, stop and status
systemctl start stardog.service
systemctl status stardog.service
systemctl stop stardog.service


# Qanary
# Qanary pipeline and other main components
git clone https://github.com/WDAqua/Qanary
cd Qanary
mvn clean install -Ddockerfile.skip=true

# running the pipeline
cd qanary_pipeline-template/target/
java -jar qa.pipeline-2.4.0.jar --qanary.triplestore=http://admin:admin@localhost:5820/qanary


# Qanary components
git clone https://github.com/WDAqua/Qanary-question-answering-components.git
cd Qanary-question-answering-components/
mvn clean install -Ddockerfile.skip=true

# running a component
cd qanary_component-REL-RELNLIOD/target/
java -jar qanary_component-REL-RELNLIOD-2.0.0.jar


# SPARQL Queries for DBpedia
DBpedia: "SELECT DISTINCT ?uri WHERE {<http://dbpedia.org/resource/Switzerland>   <http://dbpedia.org/ontology/capital> ?uri. }"

SINA: "SELECT * WHERE {?uri <http://dbpedia.org/resource/Switzerland>   <http://dbpedia.org/ontology/capital>. }"
