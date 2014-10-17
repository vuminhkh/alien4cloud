package alien4cloud.rest.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.mapping.ElasticSearchClient;
import org.elasticsearch.mapping.MappingBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.Constants;
import alien4cloud.component.model.IndexedNodeType;
import alien4cloud.component.model.Tag;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.rest.component.ComponentController;
import alien4cloud.rest.component.RecommendationRequest;
import alien4cloud.rest.component.UpdateTagRequest;
import alien4cloud.rest.model.RestErrorCode;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.tosca.container.model.ToscaElement;
import alien4cloud.tosca.container.model.type.CapabilityDefinition;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-search-test.xml")
@Slf4j
public class ComponentTest {

    @Resource
    ElasticSearchClient esclient;
    Client nodeClient;
    @Resource(name = "alien-es-dao")
    IGenericSearchDAO dao;
    @Resource
    ComponentController componentController;

    private static final String COMPONENT_INDEX = ToscaElement.class.getSimpleName().toLowerCase();
    private static final List<Tag> rootTags;
    // private static final Tag tagToUpdate1, tagToUpdate2;
    private static final Map<String, CapabilityDefinition> capabilities;
    private static final Tag TAG_1, TAG_2, INTERNAL_TAG;
    private IndexedNodeType indexedNodeType, tmpIndexedNodeType, indexedNodeType2, indexedNodeType3;

    static {
        rootTags = Lists.newArrayList();
        rootTags.add(new Tag("icon", "/usr/local/root-icon.png"));
        rootTags.add(new Tag("tag1", "Root tag1 value..."));
        rootTags.add(new Tag("tag2", "Root tag2 value..."));

        // tagToUpdate1 = new Tag("icon", "/usr/child-icon.png");

        // tagToUpdate2 = Lists.newArrayList();
        // tagToUpdate2.add(new Tag("tag2", "UPDATED - Root tag2 value..."));

        TAG_1 = new Tag("tag1", "/usr/child-icon.png");
        TAG_2 = new Tag("tag2", "UPDATED - Root tag2 value...");
        INTERNAL_TAG = new Tag("icon", "/usr/child-icon.png");

        capabilities = new HashMap<String, CapabilityDefinition>();
        capabilities.put("wor", new CapabilityDefinition("wor", "wor", 1, 1));
        capabilities.put("jdni", new CapabilityDefinition("jdni", "jdni", 1, 1));
        capabilities.put("container", new CapabilityDefinition("container", "container", 1, 1));
        capabilities.put("feature", new CapabilityDefinition("feature", "feature", 1, 1));
    }

    @Before
    public void before() {
        nodeClient = esclient.getClient();
        prepareNodeTypes();
    }

    @Test
    public void updateComponentTag() {

        // Updating root tags with tagToUpdate2
        UpdateTagRequest updateComponentRequest = new UpdateTagRequest();
        // String key = (String) tagToUpdate2.keySet().toArray()[0];
        updateComponentRequest.setTagKey(TAG_2.getName());
        updateComponentRequest.setTagValue(TAG_2.getValue());

        componentController.upsertTag(indexedNodeType.getId(), updateComponentRequest);
        tmpIndexedNodeType = dao.findById(IndexedNodeType.class, indexedNodeType.getId());

        assertEquals("Tags map size should'nt change", tmpIndexedNodeType.getTags().size(), indexedNodeType.getTags().size());
        int index = tmpIndexedNodeType.getTags().indexOf(TAG_2);
        int index2 = indexedNodeType.getTags().indexOf(TAG_2);
        assertNotEquals("tag2 tag value has changed", tmpIndexedNodeType.getTags().get(index).getValue(), indexedNodeType.getTags().get(index2).getValue());
        assertEquals("tag2 tag value should be the same as TAG_2", tmpIndexedNodeType.getTags().get(index).getValue(), TAG_2.getValue());

    }

    @Test
    public void updateComponentTagWithBadComponentId() {

        UpdateTagRequest updateComponentRequest = new UpdateTagRequest();
        // String key = (String) tagToUpdate1.keySet().toArray()[0];
        updateComponentRequest.setTagKey(TAG_2.getName());
        updateComponentRequest.setTagValue(TAG_2.getValue());

        RestResponse<Void> response = componentController.upsertTag("X", updateComponentRequest);

        assertEquals("Should have <" + RestErrorCode.COMPONENT_MISSING_ERROR.getCode() + "> error code returned", response.getError().getCode(),
                RestErrorCode.COMPONENT_MISSING_ERROR.getCode());
        assertNotNull("Error message should'nt be null", response.getError().getMessage());
    }

    @Test
    public void deleteComponentTag() {

        RestResponse<Void> response = null;

        // Remove tagToDelete1
        response = componentController.deleteTag(indexedNodeType.getId(), TAG_1.getName());
        tmpIndexedNodeType = dao.findById(IndexedNodeType.class, indexedNodeType.getId());

        assertTrue("Tag <" + TAG_1 + "> does not exist anymore", !tmpIndexedNodeType.getTags().contains(TAG_1));
        assertSame("Tag map size from initial IndexedNodeType decreased", tmpIndexedNodeType.getTags().size(), rootTags.size() - 1);
        assertNull("Delete tag operation response has no error object", response.getError());

        // Remove tagToDelete2
        response = componentController.deleteTag(indexedNodeType.getId(), TAG_2.getName());
        tmpIndexedNodeType = dao.findById(IndexedNodeType.class, indexedNodeType.getId());

        assertTrue("Tag <" + TAG_2 + "> does not exist anymore", !tmpIndexedNodeType.getTags().contains(TAG_2));

        // Remove internal tag "icon"
        response = componentController.deleteTag(indexedNodeType.getId(), INTERNAL_TAG.getName());
        assertNotNull("Tag <" + INTERNAL_TAG + "> is internal and cannot be removed", response.getError());
        assertEquals("Should have <" + RestErrorCode.COMPONENT_INTERNALTAG_ERROR.getCode() + "> error code returned", response.getError().getCode(),
                RestErrorCode.COMPONENT_INTERNALTAG_ERROR.getCode());

    }

    @Test
    public void recommendForCapabilityWhenAlreadyRecommendedTest() {
        RestResponse<IndexedNodeType> response = null;

        RecommendationRequest recRequest = new RecommendationRequest();
        recRequest.setComponentId(indexedNodeType.getId());
        recRequest.setCapability("jdni");

        response = componentController.recommendComponentForCapability(recRequest);
        assertNull(response.getError());
        assertNotNull(response.getData());
        assertTrue(response.getData().getDefaultCapabilities().contains("jdni"));

        Map<String, String[]> filters = new HashMap<>();
        filters.put(Constants.DEFAULT_CAPABILITY_FIELD_NAME, new String[] { "jdni" });
        GetMultipleDataResult result = dao.find(IndexedNodeType.class, filters, 1);
        IndexedNodeType component;
        if (result == null || result.getData() == null || result.getData().length == 0) {
            component = null;
        } else {
            component = (IndexedNodeType) result.getData()[0];
        }

        assertNotNull(component);
        assertNotNull(component.getDefaultCapabilities());
        assertTrue(component.getId().equals(recRequest.getComponentId()));
        assertTrue(component.getDefaultCapabilities().contains("jdni"));
    }

    @Test
    public void recommendForCapabilityTest() {
        RestResponse<IndexedNodeType> response = null;

        RecommendationRequest recRequest = new RecommendationRequest();
        recRequest.setComponentId(indexedNodeType.getId());
        recRequest.setCapability("wor");

        response = componentController.recommendComponentForCapability(recRequest);
        assertNull(response.getError());

        IndexedNodeType component = dao.findById(IndexedNodeType.class, recRequest.getComponentId());

        assertNotNull(component.getDefaultCapabilities());
        assertEquals(1, component.getDefaultCapabilities().size());
        assertTrue(" component of Id " + component.getId() + " should contains " + "wor", component.getDefaultCapabilities().contains("wor"));
    }

    private void prepareNodeTypes() {

        indexedNodeType = new IndexedNodeType();
        indexedNodeType.setElementId("1");
        indexedNodeType.setArchiveName("tosca.nodes.Root");
        indexedNodeType.setArchiveVersion("3.0");
        indexedNodeType.setDerivedFrom(null);
        indexedNodeType.setDescription("Root description...");
        indexedNodeType.setTags(rootTags);
        dao.save(indexedNodeType);

        indexedNodeType2 = new IndexedNodeType();
        indexedNodeType2.setElementId("2");
        indexedNodeType2.setArchiveName("tosca.nodes.Root");
        indexedNodeType2.setArchiveVersion("3.0");
        indexedNodeType2.setDerivedFrom(null);
        indexedNodeType2.setDescription("Root description...");
        indexedNodeType2.setTags(rootTags);
        indexedNodeType2.setCapabilities(new HashSet<>(capabilities.values()));
        indexedNodeType2.setDefaultCapabilities(new HashSet<String>());
        indexedNodeType2.getDefaultCapabilities().add("jdni");
        dao.save(indexedNodeType2);

        indexedNodeType3 = new IndexedNodeType();
        indexedNodeType3.setElementId("3");
        indexedNodeType3.setArchiveName("tosca.nodes.Root");
        indexedNodeType3.setArchiveVersion("3.0");
        indexedNodeType3.setDerivedFrom(null);
        indexedNodeType3.setDescription("Root description...");
        indexedNodeType3.setTags(rootTags);
        indexedNodeType3.setCapabilities(new HashSet<>(capabilities.values()));
        indexedNodeType3.setDefaultCapabilities(new HashSet<String>());
        indexedNodeType3.getDefaultCapabilities().add("container");
        dao.save(indexedNodeType3);
    }

    private void clearIndex(String indexName, Class<?> clazz) throws InterruptedException {
        String typeName = MappingBuilder.indexTypeFromClass(clazz);
        log.info("Cleaning ES Index " + COMPONENT_INDEX + " and type " + typeName);
        nodeClient.prepareDeleteByQuery(indexName).setQuery(QueryBuilders.matchAllQuery()).setTypes(typeName).execute().actionGet();
    }

    @After
    public void cleanup() throws InterruptedException {
        clearIndex(COMPONENT_INDEX, IndexedNodeType.class);
    }

}