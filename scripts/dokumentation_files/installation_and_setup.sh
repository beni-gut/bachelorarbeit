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


# SPARQL Query from QALD-9, impossible by QB?
PREFIX dbo: <http://dbpedia.org/ontology/>
PREFIX dbp: <http://dbpedia.org/property/>
PREFIX dbr: <http://dbpedia.org/resource/>
SELECT DISTINCT ?uri
WHERE {
  ?uri dbo:occupation dbr:Skateboarder {
    ?uri dbo:birthPlace dbr:Sweden
  } UNION {
    ?uri dbo:birthPlace ?place .
    ?place dbo:country dbr:Sweden
  }
}



# SPARQL Query from QALD-9, impossible by QB?
PREFIX dbo: <http://dbpedia.org/ontology/>
PREFIX dbp: <http://dbpedia.org/property/>
PREFIX dbr: <http://dbpedia.org/resource/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX db: <http://dbpedia.org/>
SELECT ?capacity
WHERE {
  {
    dbr:FC_Porto dbo:ground ?ground .
    ?ground dbo:capacity ?capacity
  } UNION {
    dbr:FC_Porto dbo:ground ?ground . ?ground dbp:capacity ?capacity
  }
}

# SPARQL Query from QALD-9, impossible by QB?
PREFIX dbo: <http://dbpedia.org/ontology/>
SELECT DISTINCT ?uri
WHERE {
  <http://dbpedia.org/resource/Lovesick_(1983_film)> dbo:starring ?uri .
  ?uri dbo:birthPlace ?city .
  ?city dbo:country <http://dbpedia.org/resource/United_Kingdom>
}



