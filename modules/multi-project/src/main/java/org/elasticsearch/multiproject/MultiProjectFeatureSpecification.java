/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.multiproject;

import org.elasticsearch.features.FeatureSpecification;
import org.elasticsearch.features.NodeFeature;

import java.util.Set;

public class MultiProjectFeatureSpecification implements FeatureSpecification {

    private static final NodeFeature MULTI_PROJECT = new NodeFeature("multi_project");

    @Override
    public Set<NodeFeature> getFeatures() {
        return Set.of(MULTI_PROJECT);
    }
}
