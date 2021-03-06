/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.kb.internal.concept;

import ai.grakn.concept.AttributeType;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.RelationshipType;
import ai.grakn.exception.GraknTxOperationException;
import ai.grakn.kb.internal.TxTestBase;
import ai.grakn.kb.internal.structure.EdgeElement;
import ai.grakn.util.ErrorMessage;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchemaConceptTest extends TxTestBase {

    @Test
    public void whenChangingSchemaConceptLabel_EnsureLabelIsChangedAndOldLabelIsDead(){
        Label originalLabel = Label.of("my original label");
        Label newLabel = Label.of("my new label");
        EntityType entityType = tx.putEntityType(originalLabel.getValue());

        //Original label works
        assertEquals(entityType, tx.getType(originalLabel));

        //Change The Label
        entityType.setLabel(newLabel);

        //Check the label is changes
        assertEquals(newLabel, entityType.getLabel());
        assertEquals(entityType, tx.getType(newLabel));

        //Check old label is dead
        assertNull(tx.getType(originalLabel));
    }

    @Test
    public void whenSpecifyingTheResourceTypeOfAnEntityType_EnsureTheImplicitStructureIsCreated(){
        Label resourceLabel = Label.of("Attribute Type");
        EntityType entityType = tx.putEntityType("Entity1");
        AttributeType attributeType = tx.putAttributeType("Attribute Type", AttributeType.DataType.STRING);

        //Implicit Names
        Label hasResourceOwnerLabel = Schema.ImplicitType.HAS_OWNER.getLabel(resourceLabel);
        Label hasResourceValueLabel = Schema.ImplicitType.HAS_VALUE.getLabel(resourceLabel);
        Label hasResourceLabel = Schema.ImplicitType.HAS.getLabel(resourceLabel);

        entityType.attribute(attributeType);

        RelationshipType relationshipType = tx.getRelationshipType(hasResourceLabel.getValue());
        Assert.assertEquals(hasResourceLabel, relationshipType.getLabel());

        Set<Label> roleLabels = relationshipType.relates().map(SchemaConcept::getLabel).collect(toSet());
        assertThat(roleLabels, containsInAnyOrder(hasResourceOwnerLabel, hasResourceValueLabel));

        assertThat(entityType.plays().collect(toSet()), containsInAnyOrder(tx.getRole(hasResourceOwnerLabel.getValue())));
        assertThat(attributeType.plays().collect(toSet()), containsInAnyOrder(tx.getRole(hasResourceValueLabel.getValue())));

        //Check everything is implicit
        assertTrue(relationshipType.isImplicit());
        relationshipType.relates().forEach(role -> assertTrue(role.isImplicit()));

        // Check that resource is not required
        EdgeElement entityPlays = ((EntityTypeImpl) entityType).vertex().getEdgesOfType(Direction.OUT, Schema.EdgeLabel.PLAYS).iterator().next();
        assertFalse(entityPlays.propertyBoolean(Schema.EdgeProperty.REQUIRED));
        EdgeElement resourcePlays = ((AttributeTypeImpl<?>) attributeType).vertex().getEdgesOfType(Direction.OUT, Schema.EdgeLabel.PLAYS).iterator().next();
        assertFalse(resourcePlays.propertyBoolean(Schema.EdgeProperty.REQUIRED));
    }

    @Test
    public void whenChangingTheLabelOfSchemaConceptAndThatLabelIsTakenByAnotherConcept_Throw(){
        Label label = Label.of("mylabel");

        EntityType e1 = tx.putEntityType("Entity1");
        tx.putEntityType(label);

        expectedException.expect(GraknTxOperationException.class);
        expectedException.expectMessage(ErrorMessage.LABEL_TAKEN.getMessage(label));

        e1.setLabel(label);
    }
}
