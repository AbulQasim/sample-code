package com.sample.listeners;

import com.sample.constants.SampleConnectorConstants;
import com.sample.models.SampleConfigReader;
import com.sample.models.SampleConfiguration;
import com.sample.services.SampleConnectorSupportService;
import com.sample.utils.SampleConnectorUtils;
import com.day.cq.commons.jcr.JcrConstants;
import com.day.cq.dam.api.DamConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.observation.JackrabbitEventFilter;
import org.apache.jackrabbit.api.observation.JackrabbitObservationManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.LoginException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Workspace;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import java.util.HashMap;

import static com.day.cq.wcm.api.NameConstants.NT_PAGE;
import static javax.jcr.observation.Event.NODE_MOVED;

/**
 * This is a listener which listens to asset or page move.
 * This is currently intended for triggering a job which updates
 * the resource url in corresponding Sample Document.
 */
@Component(service = EventListener.class, immediate = true)
public class SampleAEMAssetOrPageMoveListener implements EventListener {

    @Reference
    private SlingRepository repository;

    @Reference ResourceResolverFactory resolverFactory;

    @Reference
    private JobManager jobManager;

    @Reference
    private SampleConnectorSupportService sampleConnectorSupportService;

    private JackrabbitObservationManager observationManager;

    private Session session; // NOSONAR

    private static final Logger LOG = LoggerFactory.getLogger(SampleAssetOrPageMoveListener.class);

    @Override public void onEvent(EventIterator eventIterator) {

        ResourceResolver resourceResolver = SampleConnectorUtils
                .getServiceResourceResolver(SampleConnectorConstants.Sample_AEM_SERVICE_USER,resolverFactory);
        if(resourceResolver==null){
            LOG.error("AEM service Resource Resolver not found");
            return;
        }
        while (eventIterator.hasNext()) {
            Event event = eventIterator.nextEvent();
            try {
                String assetOrPagePath = event.getPath();
                Resource assetOrPageResource = resourceResolver.getResource(assetOrPagePath);
                if(assetOrPageResource==null){
                    LOG.error("Could not resolve asset or page {}",assetOrPageResource);
                    SampleConnectorUtils.closeResourceResolver(resourceResolver);
                    return;
                }
                    final SampleConfigReader config = assetOrPageResource.adaptTo(SampleConfigReader.class);
                    if(config==null){
                        LOG.error(
                                "Sample config from resource {{}} is null. Hence cannot handle asset move.",
                                assetOrPageResource.getPath());
                        SampleConnectorUtils.closeResourceResolver(resourceResolver);
                        return;
                    }
                    final SampleConfiguration SampleConfig = config.getSampleProps();
                    if (SampleConfig == null) {
                        LOG.error(
                                "Sample Vault cloud configuration from resource {{}} is null. Hence cannot handle asset move.",
                                assetOrPageResource.getPath());
                        SampleConnectorUtils.closeResourceResolver(resourceResolver);
                        return;
                    }
                    if(SampleConfig.getUpdateResourceUrlOnAssetPageMove().equals("true")){
                        HashMap<String, Object> jobProps = new HashMap<>();
                        jobProps.put("movedAssetOrPagePath", assetOrPagePath);
                        this.jobManager.addJob(SampleConnectorConstants.JOB_TOPIC_Sample_ASSET_PAGE_MOVE, jobProps);
                    }
            } catch (RepositoryException e) {
                LOG.error("Error occurred while handling Sample asset move");
            }
        }
        SampleConnectorUtils.closeResourceResolver(resourceResolver);

    }


    @Activate
    protected void activate() {
        try {
            session = repository.loginService(SampleConnectorConstants.Sample_AEM_SERVICE_USER, null);

            JackrabbitEventFilter jackrabbitEventFilter = new JackrabbitEventFilter()
                    .setAbsPath("/content")
                    .setEventTypes(NODE_MOVED)
                    .setIsDeep(true)
                    .setNoLocal(true)
                    .setNodeTypes(new String[] { DamConstants.NT_SLING_ORDEREDFOLDER, "sling:Folder", JcrConstants.NT_FOLDER, NT_PAGE})
                    .setNoExternal(true);

            Workspace workSpace = session.getWorkspace();
            if (null != workSpace) {
                observationManager = (JackrabbitObservationManager) workSpace.getObservationManager();
                observationManager.addEventListener(this, jackrabbitEventFilter);
                LOG.info("This Sample Asset Move Event Listener is registered at {} for the event type {}.", "/content/dam", NODE_MOVED);

            }
        } catch (LoginException loginException) {
            LOG.error("LoginException :", loginException);
        } catch (UnsupportedRepositoryOperationException exception) {
            LOG.error("UnsupportedRepositoryOperationException :", exception);
        } catch (RepositoryException repositoryException) {
            LOG.error("RepositoryException : ", repositoryException);
        }

    }

    @Deactivate
    protected void deactivate() {
        try {
            if (null != observationManager) {
                observationManager.removeEventListener(this);
                LOG.info("The Sample Asset Move Listener is removed.");
            }
        } catch (RepositoryException repositoryException) {
            LOG.error("RepositoryException : ", repositoryException);
        } finally {
            if (null != session) {
                session.logout();
            }
        }
    }
}
