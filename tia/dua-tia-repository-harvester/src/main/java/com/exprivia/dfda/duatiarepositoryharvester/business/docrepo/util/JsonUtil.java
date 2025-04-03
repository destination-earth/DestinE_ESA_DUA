package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.UnexpectedJsonStructureException;
import com.fasterxml.jackson.databind.JsonNode;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class JsonUtil {

    private List<SimpleDateFormat> dateFormatters = Arrays.asList(
        new SimpleDateFormat("yyyy/MM/dd"),
        new SimpleDateFormat("yyyy-MM-dd")
        );

    public JsonNode getSubNode(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        if (node == null) throw new UnexpectedJsonStructureException("current node is null while searching node \"" + name + "\"");

        String [] path = name.split("!");
        JsonNode subnode = node.get(path[0]);
        if (subnode == null) {
            if (required) {
                throw new UnexpectedJsonStructureException("cannot find node \"" + name + "\"");
            } else {
                return null;
            }
        }
        if (path.length == 1) {
            return subnode;
        } else {
            return getSubNode(
                subnode, 
                String.join(".", Arrays.copyOfRange(path, 1, path.length)), 
                required);
        }
    }

    public String getStringNodeValue(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        JsonNode subnode = getSubNode(node, name, required);
        if (subnode == null || subnode.isNull()) return null;

        checkNodeIsString(subnode);
        return subnode.asText();
    }

    public Integer getIntNodeValue(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        JsonNode subnode = getSubNode(node, name, required);
        if (subnode == null || subnode.isNull()) return null;

        if (!subnode.isInt())
            throw new UnexpectedJsonStructureException("node \"" + name +"\" is not an integer");

        return subnode.asInt();
    }

    public Double getDoubleNodeValue(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        JsonNode subnode = getSubNode(node, name, required);
        if (subnode == null || subnode.isNull()) return null;

        if (!subnode.isDouble())
            throw new UnexpectedJsonStructureException("node \"" + name +"\" is not a double");

        return subnode.asDouble();
    }

    public Boolean getBoolNodeValue(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        JsonNode subnode = getSubNode(node, name, required);
        if (subnode == null || subnode.isNull()) return null;

        if (!subnode.isBoolean())
            throw new UnexpectedJsonStructureException("node \"" + name +"\" is not a boolean");

        return subnode.asBoolean();
    }

    public Date getDateNodeValue(JsonNode node, String name, boolean required) throws UnexpectedJsonStructureException {
        String strValue = getStringNodeValue(node, name, required);
        if (strValue == null) return null;

        for (SimpleDateFormat sdf : dateFormatters) {
            try {
                return sdf.parse(strValue);
            } catch (ParseException e) {

            }
        }
        throw new UnexpectedJsonStructureException("cannot decode date: " + strValue);
    }

    public List<String> getStringArrayNodeValue(JsonNode node, String name, boolean required, boolean canBeSingleValue) 
        throws UnexpectedJsonStructureException {

        List<String> values = new ArrayList<>();
        JsonNode subnode = getSubNode(node, name, required);

        if (subnode != null) {
            if (subnode.isArray()) {

                for (JsonNode item : subnode) {
                    checkNodeIsString(item);
                    values.add(item.asText());
                }
            } else {
                if (!canBeSingleValue) throw new UnexpectedJsonStructureException("node \"" + name + "\" is not an array");
                values.add(subnode.asText());
            }
        }

        return values;
    }

    public void checkNodeIsString(JsonNode node) throws UnexpectedJsonStructureException {
        if (node.isObject()) throw new UnexpectedJsonStructureException("node is an object, string is expected");
        if (node.isArray()) throw new UnexpectedJsonStructureException("node is an array, string is expected");
    }
}
