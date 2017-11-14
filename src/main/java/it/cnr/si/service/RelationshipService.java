package it.cnr.si.service;

import it.cnr.si.domain.Relationship;
import it.cnr.si.flows.ng.service.AceBridgeService;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.CnrgroupRepository;
import it.cnr.si.repository.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.cnr.si.flows.ng.utils.Enum.Role.*;

/**
 * Service Implementation for managing Relationship.
 */
@Service
@Transactional
public class RelationshipService {

    private final Logger log = LoggerFactory.getLogger(RelationshipService.class);

    @Inject
    private RelationshipRepository relationshipRepository;
    @Inject
    private AceBridgeService aceService;
    @Inject
    private CnrgroupRepository cnrgroupRepository;

    /**
     * Save a relationship.
     *
     * @param relationship the entity to save
     * @return the persisted entity
     */
    @CacheEvict(value = "allGroups", allEntries = true)
    public Relationship save(Relationship relationship) {
        log.debug("Request to save Relationship : {}", relationship);
        return relationshipRepository.save(relationship);
    }

    /**
     * Get all the relationships.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<Relationship> findAll(Pageable pageable) {
        log.debug("Request to get all Relationships");
        return relationshipRepository.findAll(pageable);
    }

    /**
     * Get one relationship by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Relationship findOne(Long id) {
        log.debug("Request to get Relationship : {}", id);
        return relationshipRepository.findOne(id);
    }

    /**
     * Delete the  relationship by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Relationship : {}", id);
        relationshipRepository.delete(id);
    }

    @Cacheable(value = "allGroups", key = "#username")
    public List<GrantedAuthority> getAllGroupsForUser(String username) {
        //A) recupero la lista dei gruppi a cui appartiene direttamente l'utente
        Set<String> aceGroup = getACEGroupsForUser(username);
        //B) recupero i children dei gruppi "supervisori" e "responsabili"
        Set<String> aceGroupWithChildren = getACEChildren(aceGroup);

        //C) recupero i gruppi "associati" nel nostro db (getAllRelationship) e mergio
        return Stream.concat(aceGroupWithChildren.stream(), getAllRelationship(aceGroupWithChildren).stream())
                .distinct()
                .map(Utils::addLeadingRole)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private Set<String> getACEChildren(Set<String> aceGroup) {
        //Filtro solo i gruppi di tipo "responsabili" o "supervisori"
        Set<String> groupToSearchChildren = aceGroup.stream()
                .filter(group -> group.contains(supervisore.getValue()) ||
                        group.contains(supervisoreStruttura.getValue()) ||
                        group.contains(responsabile.getValue()) ||
                        group.contains(responsabileStruttura.getValue()))
                .collect(Collectors.toSet());
        //cerco i children dei gruppi che ho filtrato
        Set<String> children = new HashSet<>();
        for (String group : groupToSearchChildren) {
            //todo: ancora da implementare in ACE
//            children.addAll();
        }
        return Stream.concat(aceGroup.stream(), children.stream())
                .distinct()
                .collect(Collectors.toSet());
    }


    public Set<String> getAllRelationship(Set<String> aceGropupWithParents) {
        Set<String> result = new HashSet<>();
        for (String group : aceGropupWithParents) {
            //match esatto (ad es.: ra@2216 -> supervisore#acquistitrasparenza@STRUTTURA)
            result.addAll(relationshipRepository.findRelationshipGroup(group).stream()
                                  .map(Relationship::getGroupRelationship)
                                  .collect(Collectors.toSet())
            );
            //match "@STRUTTURA" (ad es. relationship: ra@STRUTTURA -> supervisore#acquistitrasparenza@STRUTTURA)
            if (group.contains("@")) {
                String role = group.substring(0, group.indexOf('@'));
                Set<Relationship> relationshipGroupForStructure = relationshipRepository.findRelationshipForStructure(
                        group.contains("@") ? role : group);

                // rimpiazzo "@STRUTTURA" nella relationship trovata con il CODICE SPECIFICO della struttura
                result.addAll(relationshipGroupForStructure.stream()
                                      .map(relationship -> {
                                          if (relationship.getGroupRelationship().contains("@")) {
                                              String struttura = group.substring(group.indexOf('@'), group.length());
                                              return Utils.replaceStruttura(relationship.getGroupRelationship(), struttura);
                                          } else
                                              return relationship.getGroupRelationship();
                                      })
                                      .collect(Collectors.toSet()));
            }
        }
        //mapping in modo da recuperare il distinct
        return result.stream()
                .distinct()
                .collect(Collectors.toSet());
    }


    public Set<String> getACEGroupsForUser(String username) {
        return new HashSet<>(aceService.getAceGroupsForUser(username));
    }
}
