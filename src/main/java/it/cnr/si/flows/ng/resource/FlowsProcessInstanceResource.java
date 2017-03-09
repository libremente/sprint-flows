package it.cnr.si.flows.ng.resource;

import com.codahale.metrics.annotation.Timed;
import it.cnr.si.repository.UserRepository;
import it.cnr.si.security.AuthoritiesConstants;
import it.cnr.si.service.UserService;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Attachment;
import org.activiti.rest.service.api.RestResponseFactory;
import org.activiti.rest.service.api.engine.AttachmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("rest/processInstances")
public class FlowsProcessInstanceResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsProcessInstanceResource.class);
    @Autowired
    protected RestResponseFactory restResponseFactory;
    @Autowired
    HistoryService historyService;
    @Autowired
    IdentityService identityService;
    @Inject
    private UserRepository userRepository;
    @Inject
    private UserService userService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private TaskService taskService;

    /**
     * Gets my instances.
     *
     * @param skipCount the skip count
     * @param maxItems  the max items
     * @param where     the where
     * @param req       the req
     * @return the my instances
     * @throws IOException the io exception
     */
    @RequestMapping(value = "myinstances", method = RequestMethod.GET)
    @ResponseBody
    @Timed
    public ResponseEntity<Map<String, Object>> getMyInstances(
            @RequestParam Optional<Integer> skipCount,
            @RequestParam Optional<Integer> maxItems,
            @RequestParam Optional<String> where,
            HttpServletRequest req) throws IOException {

//        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
//
//        RuntimeService runtimeService = processEngine.getRuntimeService();
//        RepositoryService repositoryService = processEngine.getRepositoryService();
//        TaskService taskService = processEngine.getTaskService();
//        ManagementService managementService = processEngine.getManagementService();
//        IdentityService identityService = processEngine.getIdentityService();
//        HistoryService historyService = processEngine.getHistoryService();
//        FormService formService = processEngine.getFormService();
//        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder();
//
////        todo: serve sicuramente PersistenceAuditEventRepository fare myInstance
//        List<ProcessInstance> list = runtimeService.createProcessInstanceQuery().involvedUser().processInstanceName().list();
//            List<ProcessInstanceResponse> definition = restResponseFactory.createProcessInstanceResponseList(list);
//            DataResponse response = new DataResponse();
//            response.setStart(0);
//            response.setSize(list.size());
//            response.setTotal(list.size());
//            response.setData(list);
//
//            return ResponseEntity.ok(response);

//        IdentityLinkType.
//        repositoryService.createProcessDefinitionQuery().
//                repositoryService.

//                List < Group > authorizedGroups = identityService.createGroupQuery().potentialStarter("processDefinitionId").list();

        return null;
        //        try {
        //
        //            Map<String, Object> result = workflowService.getFilteredResults( skipCount.orElse(0),
        //                                                                             maxItems.orElse(5),
        //                                                                             where.orElse(""),
        //                                                                             user);
        //
        //            return new ResponseEntity<Map<String,Object>>(result, HttpStatus.OK);
        //
        //        } catch (IOException e) {
        //            Map<String, Object> result = new HashMap<>();
        //            LOGGER.error("Sending error response", e);
        //            result.put("error", true);
        //            result.put("success", false);
        //            return new ResponseEntity<Map<String,Object>>(result, HttpStatus.INTERNAL_SERVER_ERROR);
        //        }
    }

    @RequestMapping(value = "/processes", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Map<String, Object>> getProcesses(HttpServletRequest req,
                                                            @RequestParam Map<String, String> params) {

        return null;
        //        CMISUser user = cmisService.getCMISUserFromSession(req);
        //        BindingSession session = cmisService.getCurrentBindingSession(req);
        //
        //        try {
        //            return new ResponseEntity<Map<String,Object>>(workflowService.getProcesses(user, session, params), HttpStatus.OK);
        //        } catch (IOException e) {
        //            LOGGER.error(e.getMessage(), e);
        //            Map<String, Object> response = new HashMap<>();
        //            response.put("error", e.getMessage());
        //            return new ResponseEntity<Map<String,Object>>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        //        } catch (AlfrescoResponseException e) {
        //            LOGGER.error(e.getMessage() + " " + e.getResponse(), e);
        //            return new ResponseEntity<Map<String,Object>>(e.getResponse(), HttpStatus.INTERNAL_SERVER_ERROR);
        //        }
    }


    // TODO refactor in path param
    @RequestMapping(value = "", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Secured(AuthoritiesConstants.USER)
    @Timed
    public ResponseEntity<Map<String, Object>> getProcessInstanceById(HttpServletRequest req,
                                                                      @RequestParam("processInstanceId") String processInstanceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).includeProcessVariables().singleResult();
            result.put("entity", restResponseFactory.createHistoricProcessInstanceResponse(processInstance));

            List<Attachment> processInstanceAttachments = taskService.getProcessInstanceAttachments(processInstanceId);
            List<AttachmentResponse> collect = processInstanceAttachments.stream().map(a -> restResponseFactory.createAttachmentResponse(a)).collect(Collectors.toList());
            result.put("attachments", collect);

            List<HistoricActivityInstance> historyQuery = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .orderByHistoricActivityInstanceStartTime()
                    .asc()
                    .list();

            result.put("history", restResponseFactory.createHistoricActivityInstanceResponseList(historyQuery));
        } catch (Exception e){
            LOGGER.error("Errore: ", e);
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }


    /**
     * Restituisce le Process Instances attive.
     *
     * @param req the req
     * @return the process instances actives
     */
    @RequestMapping(value = "/active", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Secured({AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN})
    @Timed
    public ResponseEntity getActiveProcessInstances(HttpServletRequest req) {
        List<ProcessInstance> processInstance = runtimeService.createProcessInstanceQuery().includeProcessVariables().list();
        return new ResponseEntity<>(restResponseFactory.createProcessInstanceResponseList(processInstance), HttpStatus.OK);
    }


    private void validateData(Map<String, Object> data) {
        // TODO throw exeption if invalid
    }

    @RequestMapping(value = "deleteWorkflow", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Timed
    public ResponseEntity<Map<String, Object>> delete (
            HttpServletRequest req,
            @RequestParam(value = "workflowId", required = true) long workflowId) {
        return null;

    }

    /* ----------- */


}
