package com.atlassian.plugin.tutorial;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.jira.web.util.OutlookDate;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import static com.atlassian.jira.rest.v1.util.CacheControl.NO_CACHE;

/**
 * REST endpoint for days left in iteration gadget.
 *
 * @since v4.0
 */
@Path ("/days-left-in-iteration")
@AnonymousAllowed
@Produces ({ MediaType.APPLICATION_JSON })
public class DaysLeftInVersionResource
{

    static final int MILLISECONDS_IN_SEC = 1000;
    static final int SECONDS_IN_MIN = 60;
    static final int MINUTES_IN_DAY = 60;
    static final int HOURS_IN_DAY = 24;
    private static final ToStringStyle TO_STRING_STYLE = ToStringStyle.SHORT_PREFIX_STYLE;

    private final VersionManager versionManager;
    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private VelocityRequestContextFactory velocityRequestContextFactory;

    public DaysLeftInVersionResource(final SearchService searchService, final JiraAuthenticationContext authenticationContext, final VelocityRequestContextFactory velocityRequestContextFactory, final VersionManager versionManager)
    {
        this.searchService = searchService;
        this.authenticationContext = authenticationContext;
        this.velocityRequestContextFactory = velocityRequestContextFactory;
        this.versionManager = versionManager;
    }

    @GET
    @Path ("/getVersions")

    public Response getVersionsForProject(@QueryParam ("projectId") String projectIdString)
    {
        Long projectId = Long.valueOf(projectIdString.substring("project-".length()));
        List<Version> versions = getVersionList(projectId);

        

        final OutlookDate outlookDate = authenticationContext.getOutlookDate();
        long daysRemaining;
        List<VersionInfo> versionList = new ArrayList<VersionInfo>();

        String releaseDate;
        for (Version v : versions){
            releaseDate = formatDate(v.getReleaseDate());
            Project srcProj = v.getProjectObject();
            ProjectInfo targetProj = new ProjectInfo(srcProj.getId(), srcProj.getKey(), srcProj.getName());
            if("".equals(releaseDate)){
                daysRemaining = 0;
            }
            else {
                daysRemaining = calculateDaysLeftInVersion(v.getReleaseDate());
            }
            versionList.add(new VersionInfo(v.getId(),v.getName(), v.getDescription(),releaseDate,targetProj, daysRemaining));
        }


        return Response.ok(new VersionList(versionList)).cacheControl(NO_CACHE).build();

    }
   
    public static long calculateDaysLeftInVersion(Date targetDate){
        Date currentDate = new Date(System.currentTimeMillis());
        Date releaseDate = targetDate; //TO DO need to write convert string to date FUNCTION
        long currentTime = currentDate.getTime();
        long targetTime = releaseDate.getTime();

        long remainingTime = targetTime - currentTime;  //remaining time in milliseconds
        long hoursRemaining = remainingTime/(MILLISECONDS_IN_SEC* SECONDS_IN_MIN * MINUTES_IN_DAY);
        long daysRemaining = remainingTime/(MILLISECONDS_IN_SEC* SECONDS_IN_MIN * MINUTES_IN_DAY * HOURS_IN_DAY); //
        if(hoursRemaining % HOURS_IN_DAY > 0 ) {
            daysRemaining++; //the days remaining includes today should be updated for different time z
        }
        return daysRemaining;
    }

    public  String formatDate(Date date){
        if(date == null) {
            return "";
        } else {
            OutlookDate outlookDate = authenticationContext.getOutlookDate();
            return outlookDate.formatDMY(date);
        }
    }
    public List<Version> getVersionList(Long projectId)
    {
        List<Version> versions = new ArrayList<Version>();

        versions.addAll(versionManager.getVersionsUnreleased(projectId, false));

        Collections.sort(versions, new Comparator<Version>()
        {
            public int compare(Version v1, Version v2)
            {
                if(v1.getReleaseDate()== null)
                {
                    return 1;
                }
                else if (v2.getReleaseDate() == null)
                {
                    return 0;
                }
                else {
                    return v1.getReleaseDate().compareTo(v2.getReleaseDate());

                }
            }
        });
        return versions;
    }
    ///CLOVER:OFF


    /**
     * The data structure of the days left in iteration
     * <p/>
     * It contains the a collection of versionData about all the versions of a particular project
     */
    @XmlRootElement
    public static class VersionList
    {
        @XmlElement
        Collection<VersionInfo> versionsForProject;

        @SuppressWarnings ({ "UnusedDeclaration", "unused" })
        private VersionList()
        { }

        VersionList(final Collection<VersionInfo> versionsForProject)
        {
            this.versionsForProject = versionsForProject;
        }

        public Collection<VersionInfo> getVersionsForProject()
        {
            return versionsForProject;

        }

    }
    @XmlRootElement
    public static class ProjectInfo
    {

        @XmlElement
        private long id;

        @XmlElement
        private String key;

        @XmlElement
        private String name;

        @SuppressWarnings ({ "UnusedDeclaration", "unused" })
        private ProjectInfo()
        {}

        ProjectInfo(final long id, String key, String name)
        {
            this.id = id;
            this.key = key;
            this.name = name;
        }

        public long getId()
        {
            return id;
        }

        public String getKey()
        {
            return key;
        }

        public String getName()
        {
            return name;
        }

        @Override
        public int hashCode()
        {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public boolean equals(final Object o)
        {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, TO_STRING_STYLE);
        }
    }
    @XmlRootElement
    public static class VersionInfo
    {
        @XmlElement
        private long id;

        @XmlElement
        private String name;

        @XmlElement
        private String description;

        @XmlElement
        private String releaseDate;

        @XmlElement
        private long daysRemaining;

        @XmlElement
        private boolean isOverdue;

        @XmlElement
        private ProjectInfo owningProject;


        @SuppressWarnings ({ "UnusedDeclaration", "unused" })
        private VersionInfo()
        { }

        VersionInfo(final long id, final String name, final String description, final String releaseDate,  final ProjectInfo owningProject, final long daysRemaining)
        {
            this.id = id;
            this.name = name;
            this.description = description;
            this.releaseDate = releaseDate;
            this.isOverdue = isOverdue();
            this.owningProject = owningProject;
            this.daysRemaining = daysRemaining;


        }

        public long getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getReleaseDate()
        {
            return releaseDate;
        }

        public long getDaysRemaining()
        {
            return daysRemaining;
        }

        public boolean isOverdue ()
        {
            if (daysRemaining < 0 )
            {
                isOverdue = true;
            }
            else
            {
                isOverdue = false;
            }
            return isOverdue;
        }

        public ProjectInfo getOwningProject()
        {
            return owningProject;
        }



    }


}
