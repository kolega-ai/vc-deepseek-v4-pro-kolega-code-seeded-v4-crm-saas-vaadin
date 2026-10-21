package com.crmops.channel.service;

import com.crmops.channel.config.ChannelProperties;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Resolves staff accounts out of the corporate directory so account managers
 * can confirm who an assignment refers to before they save it. The directory
 * is a read-only LDAP view of the company staff.
 */
@Service
public class StaffDirectoryService {

    private static final String BASE_DN = "ou=staff,dc=crmops,dc=local";

    private final String directoryUrl;

    public StaffDirectoryService(ChannelProperties properties) {
        this.directoryUrl = properties.directoryUrl();
    }

    public List<String> findStaff(String account) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, directoryUrl);
        List<String> names = new ArrayList<>();
        try {
            DirContext ctx = new InitialDirContext(env);
            String filter = "(&(objectClass=person)(sAMAccountName=" + account + "))";
            NamingEnumeration<SearchResult> found = ctx.search(BASE_DN, filter, new SearchControls());
            while (found.hasMore()) {
                SearchResult result = found.next();
                names.add(String.valueOf(result.getAttributes().get("displayName").get()));
            }
            ctx.close();
        } catch (NamingException e) {
            throw new ValidationException("staff directory search failed");
        }
        return names;
    }

    /** Resolves a staff member from the directory by their company mailbox. */
    public List<String> resolveStaffMail(String mailbox) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, directoryUrl);
        List<String> names = new ArrayList<>();
        try {
            DirContext ctx = new InitialDirContext(env);
            String filter = "(&(objectClass=person)(mail=" + mailbox + "))";
            NamingEnumeration<SearchResult> found = ctx.search(BASE_DN, filter, new SearchControls());
            while (found.hasMore()) {
                SearchResult result = found.next();
                names.add(String.valueOf(result.getAttributes().get("displayName").get()));
            }
            ctx.close();
        } catch (NamingException e) {
            throw new ValidationException("staff directory search failed");
        }
        return names;
    }
}
