package it.cnr.si.flows.ng.service;

import static it.cnr.si.flows.ng.utils.MimetypeUtils.getMimetype;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.activiti.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import it.cnr.si.flows.ng.dto.FlowsAttachment;
import it.cnr.si.flows.ng.dto.FlowsAttachment.Azione;
import it.cnr.si.flows.ng.dto.FlowsAttachment.Stato;
import it.cnr.si.security.SecurityUtils;

@Service
public class FlowsAttachmentService {

    public static final String USER_SUFFIX = "_username";
    public static final String STATO_SUFFIX = "_stato";
    public static final String FILENAME_SUFFIX = "_filename";
    public static final String MIMETYPE_SUFFIX = "_mimetype";
    public static final String NEW_ATTACHMENT_PREFIX = "__new__";
    public static final String ARRAY_SUFFIX_REGEX = "\\[\\d+\\]";

    public static final String[] SUFFIXES = new String[] {USER_SUFFIX, STATO_SUFFIX, FILENAME_SUFFIX, MIMETYPE_SUFFIX};

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsAttachmentService.class);

    @Autowired
    private TaskService taskService;

    /**
     * Servizio che trasforma i multipart file in FlowsAttachment
     * per il successivo salbvataggio sul db
     *
     * IMPORTANTE: gli <input file multiple> devono avere il prefisso NEW_ATTACHMENT_PREFIX
     * (dovrebbe essere automatizzato nel componente, e non riguardare l'API pubblica)
     */
    public Map<String, Object> extractAttachmentsVariables(MultipartHttpServletRequest req) throws IOException {
        Map<String, Object> attachments = new HashMap<>();
        Map<String, Integer> nextIndexTable = new HashMap<>();
        String taskId, taskName;

        String username = SecurityUtils.getCurrentUserLogin();
        if (req.getParameter("taskId") != null) {
            taskId = (String) req.getParameter("taskId");
            taskName = taskService.createTaskQuery().taskId(taskId).singleResult().getName();
        } else {
            taskId = "start";
            taskName = "Avvio del flusso";
        }

        Iterator<String> i = req.getFileNames();
        while (i.hasNext()) {
            String fileName = i.next();
            MultipartFile file = req.getFile(fileName);
            boolean hasPrefix = fileName.startsWith(NEW_ATTACHMENT_PREFIX);
            if (hasPrefix) {
                fileName = fileName.substring(NEW_ATTACHMENT_PREFIX.length());
                fileName = fileName.replaceAll(ARRAY_SUFFIX_REGEX, "");
                int index = getNextIndex(taskId, fileName, nextIndexTable);
                fileName = fileName +"["+ index +"]";
            }

            boolean nuovo = taskId.equals("start") || taskService.getVariable(taskId, fileName) == null;
            LOGGER.info("inserisco come variabile il file "+ fileName);

            FlowsAttachment att = new FlowsAttachment();
            att.setName(fileName);
            att.setFilename(file.getOriginalFilename());
            att.setTime(new Date());
            att.setTaskId(taskId);
            att.setTaskName(taskName);
            att.setUsername(username);
            att.setMimetype(getMimetype(file));
            att.setBytes(file.getBytes());

            if (nuovo) {
                att.setAzione(Azione.Caricamento);
            } else {
                att.setAzione(Azione.Aggiornamento);
            }

            attachments.put(fileName, att);
        }

        return attachments;
    }

    /**
     * Se ho degli attachments multipli (per esempio allegati[0])
     * Ho bisogno di salvarli con nomi univoci
     * (per poter aggiornare gli allegati gia' presenti (es. allegato[0] e allegato[1]) e caricarne di nuovi (es. allegato[2])
     * Per cui, se sto aggiornando un file, vado dritto col nomefile (es. allegato[1])
     * invece se ne sto caricando uno nuovo, ho bisogno di sapere l'ultimo indice non ancora utilizzato
     */

    private int getNextIndex(String taskId, String fileName, Map<String, Integer> nextIndexTable) {

        Integer index = nextIndexTable.get(fileName);
        if (index != null) {
            nextIndexTable.put(fileName, index+1);
            LOGGER.info("index gia' in tabella, restituisco {}", index);
            return index;
        } else {
            if (taskId.equals("start")) {
                nextIndexTable.put(fileName, 1);
                return 0;
            } else {
                index = 0;
                String variableName = fileName + "[" + index + "]";
                while ( taskService.hasVariable(taskId, variableName) == true ) {
                    variableName = fileName + "[" + (++index) + "]";
                }
                nextIndexTable.put(fileName, index+1);
                LOGGER.info("index non ancora in tabella, restituisco {}", index);
                return index;
            }
        }
    }
}
