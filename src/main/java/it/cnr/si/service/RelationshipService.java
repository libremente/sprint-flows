package it.cnr.si.service;

import com.codahale.metrics.annotation.Timed;
import it.cnr.si.domain.Relationship;
import it.cnr.si.flows.ng.service.AceBridgeService;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.CnrgroupRepository;
import it.cnr.si.repository.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.*;
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
    @Autowired(required = false)
    private AceBridgeService aceBridgeService;
    @Inject
    private CnrgroupRepository cnrgroupRepository;
    @Inject
    private MembershipService membershipService;
    @Inject
    private Environment env;


    /**
     * Save a relationship.
     *
     * @param relationship the entity to save
     * @return the persisted entity
     */
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

    public Set<Relationship> getAllRelationshipForGroup(String group) {
        return relationshipRepository.findRelationshipGroup(group);
    }

    public Set<Relationship> getRelationshipsForGroupRelationship(String groupRelationship) {
        return relationshipRepository.getRelationshipsForGroupRelationship(groupRelationship);
    }

    @Timed
    @Deprecated // questo metodo non e' ricorsivo, quindi se abbiamo gruppi nei gruppi nei gruppi non puo' funzionare
    public List<GrantedAuthority> getAllGroupsForUserOLD(String username) {

        Set<String> merged;
        if (Arrays.asList(env.getActiveProfiles()).contains("cnr")) {

            //A) recupero la lista dei gruppi a cui appartiene direttamente l'utente
            Set<String> aceGroup = getAceGroupsForUser(username);
            //B) recupero i children dei gruppi "supervisori" e "responsabili"
            // TODO ?????
            Set<String> aceGroupWithChildren = getACEChildren(aceGroup);

            //C) recupero i gruppi "associati" nel nostro db (getAllRelationship) e mergio
            merged = Stream.concat(aceGroupWithChildren.stream(), getAllRelationship(aceGroupWithChildren).stream())
                    .distinct()
                    .map(Utils::addLeadingRole)
                    .collect(Collectors.toSet());
        } else {
            // A) Se sono su OIV, carico le Membership
            merged = getLocalGroupsForUser(username);
        }

        return merged.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    // TODO attenzione: questo metodo, a differenza di getAceGroupsForUser aggiunge i ROLE_
    private Set<String> getLocalGroupsForUser(String username) {
        return membershipService.getGroupNamesForUser(username);
    }

    // TODO ??????
    @Deprecated
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
//        for (String group : groupToSearchChildren) {
        //todo: ancora da implementare in ACE
//            children.addAll();
//        }
        return Stream.concat(aceGroup.stream(), children.stream())
                .distinct()
                .collect(Collectors.toSet());
    }

    private Set<String> getAllRelationship(Set<String> aceGropupWithParents) {
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
                .collect(Collectors.toSet());
    }

    public Set<String> getAceGroupsForUser(String username) {
        return Optional.ofNullable(aceBridgeService)
                .map(aceBridgeService -> aceBridgeService.getAceGroupsForUser(username))
                .map(strings -> strings.stream())
                .orElse(Stream.empty())
                .collect(Collectors.toSet());
    }

    @Timed
    public List<String> getUsersInMyGroups(String username) {

        List<String> usersInMyGroups = new ArrayList<>();

        Set<String> newGroups = getAllGroupsForUser(username);

        List<String> myGroups = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .parallelStream()
                .map(GrantedAuthority::getAuthority)
                .map(Utils::removeLeadingRole)
                .filter(group -> group.indexOf("afferenza") <= -1)
                .filter(group -> group.indexOf("USER") <= -1)
                .filter(group -> group.indexOf("DEPARTMENT") <= -1)
                .filter(group -> group.indexOf("PREVIOUS") <= -1)
                .collect(Collectors.toList());

        if (Arrays.asList(env.getActiveProfiles()).contains("cnr")) {
            //filtro in ACE gli utenti che appartengono agli stessi gruppi dell'utente loggato
            usersInMyGroups.addAll(getUsersInGroups(myGroups));
        } else {
            //filtro in Membership gli utenti che appartengono agli stessi gruppi dell'utente loggato            
            for (String myGroup : myGroups) {
                // se qui dovesse throware null, 
                // reipostare usersInMyGroups.addAll(membershipService.findMembersInGroup(myGroup) != null ? membershipService.findMembersInGroup(myGroup) : new ArrayList<>());
                // Martin
                usersInMyGroups.addAll(membershipService.findMembersInGroup(myGroup));
            }
        }

        usersInMyGroups = usersInMyGroups.stream()
                .distinct()
                .filter(user -> !user.equals(username))
                .collect(Collectors.toList());

        return usersInMyGroups;
    }

    public Set<String> getUsersInGroups(Collection<String> myGroups) {
        Set<String> result = new HashSet<>();
        for (String myGroup : myGroups) {
            try {
                result.addAll(aceBridgeService.getUsersInAceGroup(myGroup));
            } catch (RuntimeException e) {
                log.warn("Il ruolo {} non esiste in ACE", myGroup);
            }
        }
        return result;
    }



    public Set<String> getAllGroupsForUser(String username) {

        Set<String> groups = new HashSet<>();
        groups.addAll( getAceGroupsForUser(username) );
        groups.addAll( getLocalGroupsForUser(username) );

        groups.addAll( getAllChildGroupsRecursively(groups, new HashSet<>()) );

        return groups;
    }

    public Set<String> getAllUsersInGroup(String groupName) {

        Set<String> groups = new HashSet<>();
        groups.add(groupName);

        groups.addAll( getAllParentGroupsRecursively(groups, new HashSet<>()) );

        return getUsersInGroups(groups);
    }


    private Set<String> getAllChildGroupsRecursively(Set<String> resultSoFar, Set<String> visited) {

        log.trace("resultsSoFar {}, visited {}", resultSoFar, visited);
        Set<String> buffer = new HashSet<>();

        for (String group : resultSoFar) {

            Set<Relationship> children = relationshipRepository.findRelationshipGroup(group);
            for (Relationship child : children) {
                if (!visited.contains(child.getGroupRelationship())) {
                    buffer.add(child.getGroupRelationship());
                }
            }

            if (group.contains("@")) {
                String role = group.substring(0, group.indexOf('@'));
                children = relationshipRepository.findRelationshipForStructure(role);

                for (Relationship child : children) {
                    if (!visited.contains(child.getGroupRelationship())) {
                        visited.add(group);
                        buffer.add(Utils.replaceStruttura(child.getGroupRelationship(), group.substring(group.indexOf('@'))));
                    }
                }
            }
        }

        if (!buffer.isEmpty())
            resultSoFar.addAll(getAllChildGroupsRecursively(buffer, visited));

        return buffer;

    }

    private Set<String> getAllParentGroupsRecursively(Set<String> resultSoFar, Set<String> visited) {

        log.trace("resultsSoFar {}, visited {}", resultSoFar, visited);
        Set<String> buffer = new HashSet<>();

        for (String group : resultSoFar) {

            Set<Relationship> parents = relationshipRepository.getRelationshipsForGroupRelationship(group);
            for (Relationship parent : parents) {
                if (!visited.contains(parent.getGroupName())) {
                    buffer.add(parent.getGroupName());
                }
            }

            if (group.contains("@")) {
                String role = group.substring(0, group.indexOf('@'));
                parents = relationshipRepository.findRelationshipForStructureByGroupRelationship(role);

                for (Relationship parent : parents) {
                    if (!visited.contains(parent.getGroupName())) {
                        visited.add(group);
                        buffer.add(Utils.replaceStruttura(parent.getGroupName(), group.substring(group.indexOf('@'))));
                    }
                }
            }
        }

        if (!buffer.isEmpty())
            getAllParentGroupsRecursively(buffer, visited);

        return buffer;

    }

}
