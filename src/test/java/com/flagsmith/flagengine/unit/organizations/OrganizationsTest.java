package com.flagsmith.flagengine.unit.organizations;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.organisations.OrganisationModel;
import com.flagsmith.flagengine.utils.encode.JSONEncoder;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class OrganizationsTest {

    public void testUniqueSlugProperty() throws Exception {
        String json = "{\n" +
                "    \"id\": 1,\n" +
                "    \"name\": \"test\",\n" +
                "    \"feature_analytics\": false,\n" +
                "    \"stop_serving_flags\": false,\n" +
                "    \"persist_trait_data\": false\n" +
                "}";

        JsonNode node = JSONEncoder.getMapper().readTree(json);
        OrganisationModel organisationModel = OrganisationModel.load(node, OrganisationModel.class);

        Assert.assertTrue(organisationModel.uniqueSlug().equals("1-test"));
    }
}
