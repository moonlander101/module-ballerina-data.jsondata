package io.ballerina.lib.data.jsondata.json.schema.vocabulary;

public abstract class Keyword {
    public abstract Object getKeywordValue();
    public abstract boolean evaluate(Object instance);
}
