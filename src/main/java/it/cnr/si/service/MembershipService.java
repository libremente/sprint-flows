package it.cnr.si.service;

import it.cnr.si.domain.Membership;
import it.cnr.si.flows.ng.service.AceBridgeService;
import it.cnr.si.flows.ng.utils.Utils;
import it.cnr.si.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service Implementation for managing Membership.
 */
@Service
@Transactional
public class MembershipService {

    private final Logger log = LoggerFactory.getLogger(MembershipService.class);
    
    @Inject
    private MembershipRepository membershipRepository;

    @Autowired(required = false)
    private AceBridgeService aceService;

    public Membership save(Membership membership) {
        log.debug("Request to save Membership : {}", membership);
        return membershipRepository.save(membership);
    }


    @Transactional(readOnly = true) 
    public Page<Membership> findAll(Pageable pageable) {
        log.debug("Request to get all Memberships");
        return membershipRepository.findAll(pageable);
    }

    @Transactional(readOnly = true) 
    public Membership findOne(Long id) {
        log.debug("Request to get Membership : {}", id);
        return membershipRepository.findOne(id);
    }

    /**
     *  delete the  membership by id.
     */
    public void delete(Long id) {
        log.debug("Request to delete Membership : {}", id);
        membershipRepository.delete(id);
    }


    /**
     * Get one membership by username and groupname.
     *
     * @param username  the username of the entity
     * @param groupname the groupname of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Membership findOneByUsernameAndGroupname(String username, String groupname) {
        log.debug("Request to get Membership with username {} and groupname {}", username, groupname);
        return membershipRepository.findOneByUsernameAndGroupname(username, groupname);
    }


    public Set<String> getGroupNamesForUser(String username) {
        return membershipRepository.findGroupNamesForUser(username);
    }


    public List<GrantedAuthority> getAllAdditionalAuthoritiesForUser(String username) {
        return Stream.concat(getGroupNamesForUser(username).stream(), getACEGroupsForUser(username).stream())
                .distinct()
                .map(Utils::addLeadingRole)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private Set<String> getACEGroupsForUser(String username) {
        return Optional.ofNullable(aceService)
                .map(aceBridgeService -> aceBridgeService.getAceGroupsForUser(username))
                .map(strings -> strings.stream())
                .orElse(Stream.empty())
                .collect(Collectors.toSet());
    }

    @Deprecated
    public List<String> findMembersInGroup(String groupName) {
        List<String> result = membershipRepository.findMembersInGroup(groupName);
        Optional.ofNullable(aceService)
                .map(aceBridgeService -> aceService.getUsersInAceGroup(groupName))
                .filter(strings -> !strings.isEmpty())
                .ifPresent(strings -> result.addAll(strings));
        return result;
    }


    public Page<Membership> getGroupsWithRole(Pageable pageable, String user, String role) {
        return membershipRepository.getGroupsWithRole(role, user, pageable);
    }


    public List<Membership> getMembershipByGroupName(String groupName) {
        return membershipRepository.getMembershipByGroupName(groupName);
    }


    public List<Membership> getGroupForUser(String userName) {
        return membershipRepository.getGroupForUser(userName);
    }
}
