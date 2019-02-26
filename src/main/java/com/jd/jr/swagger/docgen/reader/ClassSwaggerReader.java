package com.jd.jr.swagger.docgen.reader;

import io.swagger.models.Swagger;

import java.util.Set;

import com.jd.jr.swagger.docgen.GenerateException;

/**
 * Created by chekong on 15/4/28.
 */
public interface ClassSwaggerReader {
    Swagger read(Set<Class<?>> classes) throws GenerateException;
}
