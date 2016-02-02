### User view

Looking at the reThink Catalogue from a user's perspective, one has to distinguish if a user aims at *retrieving* an object from the catalogue or at *populating* the catalogue with entries.

When **retrieving an object from the catalogue**, the catalogue appears to users as a single, monolytic implementation.  It is accessible via ports 80 (http) and 443 (https) in order to retrieve catalgue objects using the http(s)-get primitive.  As of version 2.1, the reThink Catalogue supports storing the following objects:
* protocolstub
* hyperty
* c
* d

To retrieve objects from the reThink Catalogue, a client (e.g. a hyperty runtime environment) can access the object via the *object's resource path* that is expressed via RBNF [1] as follows: 

    <resource-path>  ::=  <hostname> "/.well-known/" <resource-type> "/" <resource-type-id>

    <hostname>  ::= "localhost"  |  <csp-domain>
    <csp-domain>  ::=   [ <url-string> "." ]  <url-string> "." <top-level-domain>
    <top-level-domain>  ::=  "de" | "com" | "org" | "fr" | "eu"
    <resource-type> ::= "protocolstub"  |  "hyperty" 
    <resource-type-id>  ::= "default"  | <identifier>
    <identifier>  ::= <url-string>
    <url-string> ::= <url-char> ...
    <url-char>  ::= <lower-case-char> | <upper-case-char> | <digit> | "_" | "-"
    <lower-case-char> ::= "a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j" 
                                | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "s" 
                                | "t" | "u" | "v" | "w" | "x" | "y" | "z"
    <upper-case-char> ::= "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H" | "I" | "J" 
                                | "K" | "L" | "M" | "N" | "O" | "P" | "Q" | "R" | "S" 
                                | "T" | "U" | "V" | "W" | "X" | "Y" | "Z"
    <digit>  ::=   "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" 

**Note:  the BNFR spec of the resource path needs to be updated, as the [most current version](https://github.com/reTHINK-project/architecture/blob/master/docs/interface-design/resource-path.md) does not reflect all possible objects that can be stored in the catalogue**

![external_view](https://github.com/reTHINK-project/dev-catalogue/blob/master/doc/internals/catalogue-external-view.png)


**References**
[1] [Routing Backus-Naur Form (RBNF): A Syntax Used to Form Encoding Rules in Various Routing Protocol Specifications](http://tools.ietf.org/html/rfc5511)
