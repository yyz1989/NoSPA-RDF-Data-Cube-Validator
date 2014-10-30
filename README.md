NoSPA RDF Data Cube Validator
=============================

### Introduction

### Requirements

JDK (>=5) and Maven if you want to compile by yourself

or 

JVM (>=5) if you want to execute a jar directly

### Installation

This tool is written in Java and managed by Maven so you can compile it easily by yourself. The first thing you need to do is

``git clone`` this repository

Then you need to do a ``mvn package`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/data-cube-validator-0.9.jar``. Note that in this case the library for Jena and Log4j is not included in this package.

In the case that you need to run this pacakge independently, you will need to do a ``mvn package assembly:single`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/data-cube-validator-0.9-jar-with-dependencies.jar``, which includes all the required libraries to run it.

### Validation
Basically, there are 3 ways to use it:
1.  Use and IDE to modify the code and property file by yourself and run the Main class;
2.  

### Performance

The constraint check IC-12, "No duplicate observations", is the most time-consuming procedure for the entire validation. The motivation of developing this tool is mainly to tackle this issue. 

An initial benchmark shows a dramatic improvement as expected:

Test file: a data cube containing 13970 observations  

Test environment: Ubuntu 14.04 with VMWare, 2 CPU cores of I5-2450M @ 2GHz, 2 GB memory, ordinary HHD

Time consumption for validating IC-12:  

Validation by SPARQL queries with Virtuoso: 1 hour 22 min  
Validation by SPARQL queries with Jena Parser: 58 min  
Validation by NoSPA: 39 sec  
