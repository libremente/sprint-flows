package it.cnr.si.flows.ng.resource;

import it.cnr.si.FlowsApp;
import it.cnr.si.flows.ng.TestServices;
import it.cnr.si.flows.ng.utils.Utils;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.task.IdentityLink;
import org.activiti.rest.common.api.DataResponse;
import org.activiti.rest.service.api.history.HistoricProcessInstanceResponse;
import org.activiti.rest.service.api.history.HistoricTaskInstanceResponse;
import org.activiti.rest.service.api.runtime.process.ProcessInstanceResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.time.Year;
import java.util.*;

import static it.cnr.si.flows.ng.TestServices.TITOLO_DELL_ISTANZA_DEL_FLUSSO;
import static it.cnr.si.flows.ng.utils.Enum.ProcessDefinitionEnum.acquisti;
import static it.cnr.si.flows.ng.utils.Enum.VariableEnum.*;
import static it.cnr.si.flows.ng.utils.Utils.ALL_PROCESS_INSTANCES;
import static it.cnr.si.flows.ng.utils.Utils.ASC;
import static org.junit.Assert.*;
import static org.springframework.http.HttpStatus.OK;


@SpringBootTest(classes = FlowsApp.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles(profiles = "test,cnr")
//@ActiveProfiles(profiles = "test,oiv")
public class FlowsProcessInstanceResourceTest {

    private static final int LOAD_TEST_PROCESS_INSTANCES = 700;
    private static int processDeleted = 0;
    @Autowired
    RuntimeService runtimeService;
    @Inject
    private HistoryService historyService;
    @Inject
    private FlowsTaskResource flowsTaskResource;
    @Inject
    private FlowsProcessInstanceResource flowsProcessInstanceResource;
    @Inject
    private TestServices util;
    @Inject
    private FlowsProcessDefinitionResource flowsProcessDefinitionResource;
    @Inject
    private RepositoryService repositoryService;

    private StopWatch stopWatch = new StopWatch();
    private ProcessInstanceResponse processInstance;
    @Inject
    private Utils utils;



    @Before
    public void setUp() {
        HttpServletRequest mockRequest = new MockHttpServletRequest();
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);
    }

    @After
    public void tearDown() {
        System.out.println(stopWatch.prettyPrint());
        processDeleted = processDeleted + util.myTearDown();
    }

    @Test
    public void testGetMyProcesses() throws Exception {
        processInstance = util.mySetUp(acquisti);
        String processInstanceID = verifyMyProcesses(1, 0);
//        La sospensione non viene usata dall`applicazione
//        // testo che, anche se una Process Instance viene sospesa, la vedo ugualmente
//        util.loginAdmin();
//        flowsProcessInstanceResource.suspend(new MockHttpServletRequest(), processInstanceID);
//
//        util.loginResponsabileAcquisti();
//        processInstanceID = verifyMyProcesses(1, 0);
        //testo che cancellandola una Process Instances NON la vedo tra i processi avviati da me
        util.loginAdmin();
        ResponseEntity response = flowsProcessInstanceResource.delete(processInstanceID, "TEST");
        assertEquals(OK, response.getStatusCode());
        util.loginResponsabileAcquisti();
        verifyMyProcesses(0, 0);
    }


    @Test(expected = AccessDeniedException.class)
    public void testGetProcessInstanceById() throws Exception {
        processInstance = util.mySetUp(acquisti);

        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) flowsProcessInstanceResource.getProcessInstanceById(processInstance.getId(), true);
        assertEquals(OK, response.getStatusCode());

        HistoricProcessInstanceResponse entity = (HistoricProcessInstanceResponse) ((HashMap) response.getBody()).get("entity");
        assertEquals(processInstance.getId(), entity.getId());
        assertEquals(processInstance.getBusinessKey(), entity.getBusinessKey());
        assertEquals(processInstance.getProcessDefinitionId(), entity.getProcessDefinitionId());

        HashMap appo = (HashMap) ((HashMap) response.getBody()).get("identityLinks");
        assertEquals(2, appo.size());
        HashMap identityLinks = (HashMap) appo.get(appo.keySet().toArray()[1]); // TODO
        assertEquals("[${gruppoSFD}]", identityLinks.get("candidateGroups").toString());
        assertEquals(0, ((HashSet) identityLinks.get("candidateUsers")).size());
        assertEquals(1, ((ArrayList) identityLinks.get("links")).size());
        assertNull(identityLinks.get("assignee"));

        HashMap history = (HashMap) ((ArrayList) ((HashMap) response.getBody()).get("history")).get(0);
        assertEquals(processInstance.getId(), ((HistoricTaskInstanceResponse) history.get("historyTask")).getProcessInstanceId());
        assertEquals(1, ((ArrayList) history.get("historyIdentityLink")).size());

        HashMap attachments = (HashMap) ((HashMap) response.getBody()).get("attachments");
        assertEquals(0, attachments.size());

        //verifica che gli utenti con ROLE_ADMIN POSSANO accedere al servizio
        util.loginAdmin();
        flowsProcessInstanceResource.getProcessInstanceById(processInstance.getId(), false);

        //verifica AccessDeniedException (risposta 403 Forbidden) in caso di accesso di utenti non autorizzati
        util.loginUser();
        flowsProcessInstanceResource.getProcessInstanceById(processInstance.getId(), false);
    }


    @Test
    public void testSuspend() throws Exception {
        processInstance = util.mySetUp(acquisti);
        assertEquals(false, processInstance.isSuspended());
        //solo admin può sospendere il flow
        util.loginAdmin();
        ProcessInstanceResponse response = flowsProcessInstanceResource.suspend(new MockHttpServletRequest(), processInstance.getId());
        assertEquals(true, response.isSuspended());
    }


    @Test
    public void testGetVariable() throws Exception {
        processInstance = util.mySetUp(acquisti);
//        processInstance = util.mySetUp(iscrizioneElencoOiv);

    }


    @Test
    public void testAddAndDeleteGroupIdentityLink() throws Exception {
        processInstance = util.mySetUp(acquisti);
        //le funzionalità può essere acceduta solo con privileggi "ADMIN"
        util.loginAdmin();

        //test-junit@2216 come GRUPPO "VISUALIZZATORE"
        String groupId = "test-junit@2216";
        String userId = null;
        List<IdentityLink> identityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertFalse(identityLinks.stream()
                            .anyMatch(a -> a.getGroupId() == groupId &&
                                    a.getProcessInstanceId().equals(processInstance.getId())  &&
                                    a.getType() == Utils.PROCESS_VISUALIZER));

        ResponseEntity<Void> response = flowsProcessInstanceResource.setIdentityLink(processInstance.getId(), Utils.PROCESS_VISUALIZER, groupId, userId);

        assertEquals(OK, response.getStatusCode());
        List<IdentityLink> newIdentityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertEquals(newIdentityLinks.size(), identityLinks.size() + 1);

        assertTrue(newIdentityLinks.stream()
                           .anyMatch(a -> a.getGroupId() == groupId &&
                                   a.getProcessInstanceId().equals(processInstance.getId())  &&
                                   a.getType() == Utils.PROCESS_VISUALIZER));

        //cancellazione IdentityLink aggiunto prima
        response = flowsProcessInstanceResource.deleteIdentityLink(processInstance.getId(), Utils.PROCESS_VISUALIZER, groupId, userId);

        newIdentityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertEquals(OK, response.getStatusCode());
        assertFalse(newIdentityLinks.stream()
                            .anyMatch(a -> a.getGroupId() == groupId &&
                                    a.getProcessInstanceId().equals(processInstance.getId())  &&
                                    a.getType() == Utils.PROCESS_VISUALIZER));
    }


    @Test
    public void testAddAndDeleteUserIdentityLink() throws Exception {
        processInstance = util.mySetUp(acquisti);
        //le funzionalità può essere acceduta solo con privileggi "ADMIN"
        util.loginAdmin();

        //test-junit come UTENTE "VISUALIZZATORE"
        String groupId = null;
        String userId = "test-junit";
        List<IdentityLink> identityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertFalse(identityLinks.stream()
                            .anyMatch(a -> a.getUserId() == userId &&
                                    a.getProcessInstanceId().equals(processInstance.getId())  &&
                                    a.getType() == Utils.PROCESS_VISUALIZER));

        ResponseEntity<Void> response = flowsProcessInstanceResource.setIdentityLink(processInstance.getId(), Utils.PROCESS_VISUALIZER, groupId, userId);

        assertEquals(OK, response.getStatusCode());
        List<IdentityLink> newIdentityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertEquals(newIdentityLinks.size(), identityLinks.size() + 1);

        assertTrue(newIdentityLinks.stream()
                           .anyMatch(a -> a.getUserId() == userId &&
                                   a.getProcessInstanceId().equals(processInstance.getId())  &&
                                   a.getType() == Utils.PROCESS_VISUALIZER));

        //cancellazione IdentityLink aggiunto prima
        response = flowsProcessInstanceResource.deleteIdentityLink(processInstance.getId(), Utils.PROCESS_VISUALIZER, groupId, userId);

        newIdentityLinks = runtimeService.getIdentityLinksForProcessInstance(processInstance.getId());
        assertEquals(OK, response.getStatusCode());
        assertFalse(newIdentityLinks.stream()
                            .anyMatch(a -> a.getUserId() == userId &&
                                    a.getProcessInstanceId().equals(processInstance.getId())  &&
                                    a.getType() == Utils.PROCESS_VISUALIZER));
    }


    @Test
    public void getProcessInstancesForTrasparenzaTest() throws Exception {
        processInstance = util.mySetUp(acquisti);

        util.loginPortaleCnr();
        ResponseEntity<List<Map<String, Object>>> res = flowsProcessInstanceResource
                .getProcessInstancesForTrasparenza(acquisti.getValue(), 2018, Year.now().getValue(), 0, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());

        //prova recupero 5 elementi dopo il sevondo (result = 0 perchè ho 1 Process Instance in totale)
        res = flowsProcessInstanceResource
                .getProcessInstancesForTrasparenza(acquisti.getValue(), 2018, Year.now().getValue(), 2, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(0, res.getBody().size());


        //prova senza ordinamento
        res = flowsProcessInstanceResource
                .getProcessInstancesForTrasparenza(acquisti.getValue(), 2018, Year.now().getValue(), 0, 10, null);

        assertEquals(OK, res.getStatusCode());
        //prendo anche le Pi create negli altri test
        assertEquals(1, res.getBody().size());


        //prova anni sbagliati (Result set vuoto)
        res = flowsProcessInstanceResource
                .getProcessInstancesForTrasparenza(acquisti.getValue(), 2016, Year.now().getValue() - 1, 0, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(0, res.getBody().size());
    }



    @Test
    public void getProcessInstancesForURPTest() throws Exception {
        processInstance = util.mySetUp(acquisti);

        util.loginPortaleCnr();
        ResponseEntity<List<Map<String, Object>>> res = flowsProcessInstanceResource
                .getProcessInstancesForURP(acquisti.getValue(), 2018, Year.now().getValue(), 0, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());

        //prova recupero 5 elementi dopo il sevondo (result = 0 perchè ho 1 Process Instance in totale)
        res = flowsProcessInstanceResource
                .getProcessInstancesForURP(acquisti.getValue(), 2018, Year.now().getValue(), 2, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(0, res.getBody().size());


        //prova senza ordinamento
        res = flowsProcessInstanceResource
                .getProcessInstancesForURP(acquisti.getValue(), 2018, Year.now().getValue(), 0, 10, null);

        assertEquals(OK, res.getStatusCode());
        //prendo anche le Pi create negli altri test
        assertEquals(1, res.getBody().size());


        //prova anni sbagliati (Result set vuoto)
        res = flowsProcessInstanceResource
                .getProcessInstancesForURP(acquisti.getValue(), 2016, Year.now().getValue() - 1, 0, 10, ASC);

        assertEquals(OK, res.getStatusCode());
        assertEquals(0, res.getBody().size());
    }


    private String verifyMyProcesses(int startedByRA, int startedBySpaclient) {
        String proceeeInstanceID = null;
        // Admin vede la Process Instance che ha avviato
        Map<String, String> searchParams = new HashMap<>();

        ResponseEntity<Map<String, Object>> response = flowsProcessInstanceResource.getMyProcessInstances(0, 10, ASC, true, ALL_PROCESS_INSTANCES, searchParams);
        assertEquals(OK, response.getStatusCode());

        ArrayList processInstances = (ArrayList) ((DataResponse)response.getBody()).getData();
        assertEquals(startedByRA, processInstances.size());
        if (processInstances.size() > 0)
            proceeeInstanceID = ((HistoricProcessInstanceResponse)processInstances.get(0)).getId() ;

        // User NON vede la Process Instance avviata da Admin
        util.loginUser();

        response = flowsProcessInstanceResource.getMyProcessInstances(0, 10, ASC, true, ALL_PROCESS_INSTANCES, searchParams);
        assertEquals(OK, response.getStatusCode());
        assertEquals(startedBySpaclient, ((ArrayList) ((DataResponse)response.getBody()).getData()).size());
        util.loginResponsabileAcquisti();
        return proceeeInstanceID;
    }
}