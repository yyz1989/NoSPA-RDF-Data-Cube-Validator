RDF Data Cube Validator
=======================

### Developing...

The functions for the first 12 integrity constraints check are completed.

The constraint check IC-12, "No duplicate observations", is the most time-consuming procedure for the entire validation. The motivation of developing this tool is mainly to tackle this issue. 

An initial benchmark shows a dramatic improvement as expected:

Test file: a data cube containing 13970 observations  

Test environment: Ubuntu 14.04 with VMWare, 2 CPU cores of I5-2450M @ 2GHz, 2 GB memory, ordinary HHD

Time consumption for validating IC-12:  

Validation by SPARQL queries with Virtuoso: 1 hour 22 min  
Validation by SPARQL queries with Jena Parser: 58 min  
Validation by nesting Jena model search functions (this tool): 39 sec  
