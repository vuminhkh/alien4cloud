package alien4cloud.dao;

import java.beans.IntrospectionException;
import java.io.IOException;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedModelUtils;
import alien4cloud.component.model.IndexedToscaElement;
import alien4cloud.csar.model.Csar;
import alien4cloud.dao.ESGenericSearchDAO;
import alien4cloud.exception.IndexingServiceException;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.Cloud;
import alien4cloud.model.cloud.CloudConfiguration;
import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.common.MetaPropConfiguration;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.plugin.Plugin;
import alien4cloud.plugin.PluginConfiguration;
import alien4cloud.tosca.container.model.ToscaElement;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.container.model.topology.TopologyTemplate;
import alien4cloud.tosca.container.serializer.BoundSerializer;
import alien4cloud.utils.JSonMapEntryArraySerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Elastic Search DAO for alien 4 cloud application.
 *
 * @author luc boutier
 */
@Component("alien-es-dao")
public class ElasticSearchDAO extends ESGenericSearchDAO {

    /**
     * Initialize the dao after being loaded by spring (Create the indexes).
     */
    @PostConstruct
    public void initEnvironment() {
        // init ES annotation scanning
        try {
            getMappingBuilder().initialize("alien4cloud");
        } catch (IntrospectionException | IOException e) {
            throw new IndexingServiceException("Could not initialize elastic search mapping builder", e);
        }
        // init indices and mapped classes
        setJsonMapper(new ElasticSearchMapper());

        initIndices(ToscaElement.class.getSimpleName().toLowerCase(), false, IndexedModelUtils.getAllIndexClasses());
        initIndices(ToscaElement.class.getSimpleName().toLowerCase(), false, IndexedToscaElement.class);

        initIndice(Application.class);
        initIndice(ApplicationVersion.class);
        initIndice(ApplicationEnvironment.class);
        initIndice(DeploymentSetup.class);
        initIndice(Topology.class);
        initIndice(Csar.class);
        initIndice(Plugin.class);
        initIndice(PluginConfiguration.class);
        initIndice(TopologyTemplate.class);
        initIndice(MetaPropConfiguration.class);
        initIndice(Cloud.class);
        initIndice(CloudConfiguration.class);
        initIndice(Deployment.class);
        initIndice(CloudImage.class);
        initCompleted();
    }

    private void initIndice(Class<?> clazz) {
        initIndices(clazz.getSimpleName().toLowerCase(), false, clazz);
    }

    public static class ElasticSearchMapper extends ObjectMapper {
        private static final long serialVersionUID = 1L;

        public ElasticSearchMapper() {
            super();
            this._serializationConfig = this._serializationConfig.withAttribute(BoundSerializer.BOUND_SERIALIZER_AS_NUMBER, "true");
            this._serializationConfig = this._serializationConfig.withAttribute(JSonMapEntryArraySerializer.MAP_SERIALIZER_AS_ARRAY, "true");
            this._deserializationConfig = this._deserializationConfig.withAttribute(JSonMapEntryArraySerializer.MAP_SERIALIZER_AS_ARRAY, "true");
        }
    }
}