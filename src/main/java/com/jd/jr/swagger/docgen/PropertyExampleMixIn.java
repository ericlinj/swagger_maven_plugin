package com.jd.jr.swagger.docgen;

import com.fasterxml.jackson.annotation.JsonRawValue;

abstract class PropertyExampleMixIn {
    PropertyExampleMixIn() { }
    
    @JsonRawValue abstract String getExample();
}
