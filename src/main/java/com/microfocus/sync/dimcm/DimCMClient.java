package com.microfocus.sync.dimcm;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import merant.adm.dimensions.cmds.AdmCmd;
import merant.adm.dimensions.cmds.helper.IDeploymentViewConstants;
import merant.adm.dimensions.cmds.helper.RangeInfo;
import merant.adm.dimensions.cmds.interfaces.CmdArguments;
import merant.adm.dimensions.cmds.interfaces.Relatable;
import merant.adm.dimensions.objects.BaseDatabase;
import merant.adm.dimensions.objects.DeploymentHistoryRecord;
import merant.adm.dimensions.objects.DeploymentViewContext;
import merant.adm.dimensions.objects.collections.FilterCriterion;
import merant.adm.dimensions.objects.core.AdmAttrNames;
import merant.adm.dimensions.objects.core.AdmObject;
import merant.adm.dimensions.objects.userattrs.FilterImpl;
import merant.adm.exception.AdmException;
import merant.adm.exception.AdmObjectException;
import merant.adm.framework.Cmd;

import com.serena.dmclient.api.Baseline;
import com.serena.dmclient.api.BaselineDetails;
import com.serena.dmclient.api.DeploymentDetails;
import com.serena.dmclient.api.DimensionsArObject;
import com.serena.dmclient.api.DimensionsConnection;
import com.serena.dmclient.api.DimensionsConnectionDetails;
import com.serena.dmclient.api.DimensionsConnectionManager;
import com.serena.dmclient.api.DimensionsDatabase;
import com.serena.dmclient.api.DimensionsLcObject;
import com.serena.dmclient.api.DimensionsObjectFactory;
import com.serena.dmclient.api.DimensionsResult;
import com.serena.dmclient.api.DimensionsRuntimeException;
import com.serena.dmclient.api.FileArea;
import com.serena.dmclient.api.Filter;
import com.serena.dmclient.api.Filter.Criterion;
import com.serena.dmclient.api.LoginFailedException;
import com.serena.dmclient.api.Project;
import com.serena.dmclient.api.SystemAttributes;
import com.serena.dmclient.collections.BuildStages;
import com.serena.dmclient.objects.Lifecycle;
import com.serena.dmclient.objects.Product;

public class DimCMClient {
    public static final String OUT_PROP_NAME = "OUTPUT_DATA";

    private static final String ERROR_STR_DB = "Error: Could not connect to database %s@%s. Please check the database name and connection and verify that the remote listener is running";
    private static final String ERROR_STR_CREDS = "Error: User authentication failed";
    private static final String ERROR_STR_HOST = "Error: An unknown host or IP address was provided";

    private static final String DIMCM_AUTH_ERROR_CODE = "PRG4500325E";
    private static final String DIMCM_DB_ERROR_CODE = "Could not connect to database";

    private static final String DIMCM_DEPLOYMENT_STATUS__OK = "2";
    private static final String DIMCM_DEFAULT_LIFECYCLE = "LC_DM_STAGE";

    private static final List<String> DEPLOYMENT_ATTRIBUTES = new ArrayList<String>();
    private static final List<String> DEPLOYMENT_ATTRIBUTES_DEBUG = new ArrayList<String>();

    private static final int MILISECONDS_IN_24_HOURS = 24 * 60 * 60 * 1000;

    private static final SimpleDateFormat DIMCM_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.SSS'Z'");

    private static final Comparator<AdmObject> AMD_OBJ_BY_DATE_COMPARATOR = new Comparator<AdmObject>() {
        @Override
        public int compare(AdmObject o1, AdmObject o2) {
            try {
                String dateStr1 = o1.getAttrValue(AdmAttrNames.HISTORY_EVENT_DATE).toString();
                String dateStr2 = o2.getAttrValue(AdmAttrNames.HISTORY_EVENT_DATE).toString();
                Date d1 = DIMCM_DATE_FORMAT.parse(dateStr1);
                Date d2 = DIMCM_DATE_FORMAT.parse(dateStr2);
                return d2.compareTo(d1); // INVERSED ORDER! - we need latest date first
            } catch (AdmObjectException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return 0;
        }
    };

    static {
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.ID);// Name
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.ITEMFILE_FILENAME);// Name
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.FULL_PATH_NAME);// Details
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.REVISION);// Details
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.DESCRIPTION);// Details
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.HISTORY_EVENT_TYPE);// event type
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.HISTORY_EVENT_DESC);// event desc
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.COMMENT);// reason ???
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.HISTORY_EVENT_RESULT);// event_result
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.HISTORY_EVENT_DATE);// event date
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.HISTORY_EVENT_USER); // event by
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.FROM_STAGE);// from stage
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.TO_STAGE);// to stage
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.AREA_ID);// area
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.PROJECT_NAME); // project
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.PRODUCT_NAME); // product
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.JOB_NAME);
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.CSJ_JOB_NAME);
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.DEPLOY_AREA_VERSION);
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.REVISION); // for item
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.WSET_IS_STREAM); //
        DEPLOYMENT_ATTRIBUTES_DEBUG.add(AdmAttrNames.PARENT_JOB_ID); // parent Job ID

        DEPLOYMENT_ATTRIBUTES.add(AdmAttrNames.HISTORY_EVENT_RESULT); // parent Job ID
        DEPLOYMENT_ATTRIBUTES.add(AdmAttrNames.HISTORY_EVENT_DATE); // parent Job ID
        DEPLOYMENT_ATTRIBUTES.add(AdmAttrNames.JOB_NAME); // parent Job ID
        DEPLOYMENT_ATTRIBUTES.add(AdmAttrNames.COMMENT); // parent Job ID
    }

    public enum GetOptions {
        PROJECTS_AND_STREAMS, STREAMS, PROJECTS
    }

    public enum EntityType {
        BASELINE, STREAM, PROJECT;

        public static EntityType parseType(String type) {
            if (type.toUpperCase().equals("BASELINE")) {
                return BASELINE;
            }
            if (type.toUpperCase().equals("STREAM")) {
                return STREAM;
            }
            if (type.toUpperCase().equals("PROJECT")) {
                return PROJECT;
            }
            throw new IllegalArgumentException("There is no such entity type as '" + type + "' !");
        }
    }

    public enum AreaType {
        DEPLOYMENT("DEPLOYMENT"), WORK("WORK"), LIBRARY_CACHE("LIBRARY CACHE");

        final String type;

        private AreaType(String type) {
            this.type = type;
        }
    }

    private DimensionsConnection connection;

    public DimensionsOperations() {
    }

    public DimensionsOperations(Properties props) {
        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String dbName = props.getProperty("dbName");
        String dbConn = props.getProperty("dbConn");
        String server = props.getProperty("server");
        connect(username, password, dbName, dbConn, server);
    }

    public void connect(String username, String password, String dbName, String dbConn, String server) {
        try {
            InetAddress ia = InetAddress.getByName(server);
            if (!ia.isReachable(5000)) {
                throw new RuntimeException("Error: An unknown host or IP address was provided - " + server);
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Error: An unknown host or IP address was provided - " + server, e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            DimensionsConnectionDetails details = new DimensionsConnectionDetails();
            details.setUsername(username);
            details.setPassword(password);
            details.setDbName(dbName);
            details.setDbConn(dbConn);
            details.setServer(server);
            connection = DimensionsConnectionManager.getConnection(details);
        } catch (LoginFailedException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(DIMCM_AUTH_ERROR_CODE)) {
                System.err.println(ERROR_STR_CREDS);
            }
            if (e.getMessage() != null && e.getMessage().startsWith(DIMCM_DB_ERROR_CODE)) {
                System.err.println(String.format(ERROR_STR_DB, dbName, dbConn));
            }
            throw e;
        } catch (DimensionsRuntimeException e) {
            if (isCausedBy(e, UnknownHostException.class)) {
                System.err.println(ERROR_STR_HOST);
            }
            throw e;
        }
        System.out.println("Connection established");
    }

    public List<String> getProducts() {
        DimensionsObjectFactory factory = connection.getObjectFactory();
        @SuppressWarnings("unchecked")
        List<Product> products = factory.getBaseDatabase().getProducts();

        List<String> res = new ArrayList<String>();
        for (Product p : products) {
            res.add(p.getName());
        }
        return res;
    }

    public List<String> getProjectsStreams(String productName) {
        return getProjectsStreams(productName, GetOptions.PROJECTS_AND_STREAMS);
    }

    @SuppressWarnings("unchecked")
    public List<String> getProjectsStreams(String productName, GetOptions opts) {
        productName = prepareDimCMParam(productName);
        DimensionsObjectFactory factory = connection.getObjectFactory();
        productShouldExist(factory, productName);

        Filter filter = new Filter();
        filter.criteria().add(new Filter.Criterion(SystemAttributes.PRODUCT_NAME, productName, Filter.Criterion.EQUALS));

        switch (opts) {
            case GetOptions.STREAMS:
                filter.criteria().add(new Filter.Criterion(SystemAttributes.WSET_IS_STREAM, "Y", Filter.Criterion.EQUALS));
                break;
            case GetOptions.PROJECTS:
                filter.criteria().add(new Filter.Criterion(SystemAttributes.WSET_IS_STREAM, "Y", Filter.Criterion.NOT));
                break;
            default:
                break;
        }

        List<Project> projects = factory.getProjects(filter);

        List<String> res = new ArrayList<String>();
        for (Project proj : projects) {
            res.add(cutProductName(proj.getName()));
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public List<String> getBaselines(String productName) {
        productName = prepareDimCMParam(productName);
        DimensionsObjectFactory factory = connection.getObjectFactory();
        productShouldExist(factory, productName);

        Filter filter = new Filter();
        filter.criteria().add(new Filter.Criterion(SystemAttributes.PRODUCT_NAME, productName, Filter.Criterion.EQUALS));
        filter.criteria().add(new Filter.Criterion(SystemAttributes.BASELINE_MODE, "Release", Filter.Criterion.EQUALS));

        List<Baseline> baselines = factory.getBaselines(filter);

        List<String> res = new ArrayList<String>();
        for (Baseline baseline : baselines) {
            res.add(cutProductName(baseline.getName()));
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStages() {
        DimensionsObjectFactory factory = connection.getObjectFactory();
        BuildStages bs = factory.getBaseDatabaseAdmin().getBuildStages();

        List<String> res = new ArrayList<String>();
        for (Iterator<String> i = bs.iterator(); i.hasNext();) {
            res.add(i.next());
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    public List<String> getAreas(
            String projectFilter,
            String stageFilter,
            AreaType... types) {
        DimensionsObjectFactory factory = connection.getObjectFactory();
        projectFilter = prepareDimCMParam(projectFilter);
        stageFilter = prepareDimCMParam(stageFilter);

        Project proj = null;
        if (!projectFilter.isEmpty()) {
            proj = getProjectIfExists(factory, projectFilter, false);
        }

        List<FileArea> areas = null;

        if (proj != null) {
            areas = proj.getFileAreas();
            System.out.println("WARNING: The specified stream or project name does not exist - " + projectFilter);
        } else {
            Filter filter = new Filter();
            if (types.length > 0) {
                filter.criteria().add(Filter.Criterion.START_OR);
                for (AreaType a : types) {
                    filter.criteria().add(new Filter.Criterion(SystemAttributes.TYPE_NAME, a.type, Filter.Criterion.EQUALS));
                }
                filter.criteria().add(Filter.Criterion.END_OR);
            }
            areas = factory.getBaseDatabase().getFileArea(filter);
        }

        List<String> result = new ArrayList<String>();
        if (!stageFilter.isEmpty()) {
            stageShouldExist(factory, stageFilter);

            for (FileArea area : areas) {
                if (area.getStageID().equals(stageFilter)) {
                    result.add(area.getName());
                }
            }
        } else {
            for (FileArea area : areas) {
                result.add(area.getName());
            }
        }

        return result;
    }

    public DimensionsResult createBaseline(
            String product,
            String projectName,
            String baselineName,
            String baselineType,
            String attributeNames) {
        product = prepareDimCMParam(product);
        projectName = prepareDimCMParam(projectName);
        baselineName = prepareDimCMParam(baselineName);
        DimensionsObjectFactory factory = connection.getObjectFactory();

        BaselineDetails bd = new BaselineDetails(product, baselineName);
        bd.setTypeName(baselineType);
        bd.setBasedOnProject(product + ":" + projectName);

        if (!attributeNames.isEmpty()) {
            String[] attributePairs = attributeNames.split("\n");
            for (String pair : attributePairs) {
                String[] attributeArray = pair.split("=");
                bd.setAttribute(factory.getAttributeNumber(attributeArray[0], Baseline.class), attributeArray[1]);
            }
        }

        return factory.createBaseline(bd, DimensionsObjectFactory.BL_PROJECT);
    }

    public DimensionsResult promoteBaseline(
            String product,
            String project,
            String baseline,
            String stage,
            boolean deploy,
            String areaNames,
            String comment,
            String sraRequestID) {
        product = prepareDimCMParam(product);
        baseline = prepareDimCMParam(baseline);
        stage = prepareDimCMParam(stage);

        DimensionsObjectFactory factory = connection.getObjectFactory();

        System.out.println(String.format("Promoting baseline %s to stage %s", baseline, stage));

        StringBuilder command = new StringBuilder();
        command.append("PMBL ");
        command.append(cleverEscapeString(product + ":" + baseline));
        command.append(" /COMMENT=");
        command.append(escapeString(comment + "[" + sraRequestID + "]"));
        if (!project.isEmpty()) {
            command.append(" /WORKSET=");
            command.append(cleverEscapeString(product + ":" + project));
        }
        if (!stage.isEmpty()) {
            command.append(" /STAGE=");
            command.append(escapeString(stage));
        }

        if (deploy) {
            command.append(" /DEPLOY");
            if (!areaNames.isEmpty() && !areaNames.trim().equals("ALL")) {
                command.append(" /AREA_LIST=");
                command.append(formatAttributes(areaNames.toUpperCase()));
            }
        } else {
            command.append(" /NODEPLOY");
        }

        System.out.println("EXECUTING: " + command.toString());
        return factory.runCommand(command.toString());
    }

    public DimensionsResult demoteBaseline(
            String product,
            String project,
            String baseline,
            String stage,
            boolean deploy,
            String areaNames,
            String comment,
            String sraRequestID) {
        product = prepareDimCMParam(product);
        baseline = prepareDimCMParam(baseline);
        stage = prepareDimCMParam(stage);

        DimensionsObjectFactory factory = connection.getObjectFactory();

        System.out.println(String.format("Demoting baseline %s to stage %s", baseline, stage));

        StringBuilder command = new StringBuilder();
        command.append("DMBL ");
        command.append(cleverEscapeString(product + ":" + baseline));
        command.append(" /COMMENT=");
        command.append(escapeString(comment + "[" + sraRequestID + "]"));
        if (!project.isEmpty()) {
            command.append(" /WORKSET=");
            command.append(cleverEscapeString(product + ":" + project));
        }
        if (!stage.isEmpty()) {
            command.append(" /STAGE=");
            command.append(escapeString(stage));
        }

        if (deploy) {
            command.append(" /DEPLOY");

            if (!areaNames.isEmpty() && !areaNames.trim().equals("ALL")) {
                command.append(" /AREA_LIST=");
                command.append(formatAttributes(areaNames.toUpperCase()));
            }
        } else {
            command.append(" /NODEPLOY");
        }

        System.out.println("EXECUTING: " + command.toString());
        return factory.runCommand(command.toString());
    }

    public DimensionsResult action(
            String productName,
            EntityType objectType,
            String objectName,
            String targetState,
            String comment) {
        productName = prepareDimCMParam(productName);
        objectName = prepareDimCMParam(objectName);

        DimensionsObjectFactory factory = connection.getObjectFactory();
        productShouldExist(factory, productName);

        DimensionsLcObject entity = null;
        switch (objectType) {
            case EntityType.BASELINE:
                entity = getBaseline(factory, productName, objectName);
                break;
            case EntityType.PROJECT:
            case EntityType.STREAM:
                entity = getProjectIfExists(factory, productName + ":" + objectName);
                break;
            default:
                break;
        }

        if (entity == null) {
            throw new RuntimeException(String.format("Error: there is no %s - %s:%s", objectType, productName, objectName));
        }

        return entity.actionTo(targetState, null, comment);
    }

    public DimensionsResult deployBaseline(
            String product,
            String project,
            String baseline,
            String areas,
            String comment,
            String sraRequestID) {
        product = prepareDimCMParam(product);
        project = prepareDimCMParam(project);
        baseline = prepareDimCMParam(baseline);

        Set<String> areasSet = null;
        if (!areas.isEmpty() && !areas.equals("ALL")) {
            areasSet = new HashSet<String>();
            for (String s : trimTextAreaLines(areas).split("\n")) {
                areasSet.add(prepareDimCMParam(s));
            }
            System.out.println("Specified areas: " + areasSet);
        }

        DimensionsObjectFactory factory = connection.getObjectFactory();
        productShouldExist(factory, product);

        Baseline baselineObj = getBaseline(factory, product, baseline);
        Project projectObj = getProjectIfExists(factory, product, project);

        DeploymentDetails deployDetails = new DeploymentDetails();

        List<FileArea> areasList = projectObj.getFileAreas();
        List<FileArea> areasToDeploy = null;
        String stage = getBaselineCurrentStage(baselineObj);
        System.out.println("BASELINE STAGE = " + stage);
        if(stage == null ){
            areasToDeploy = new ArrayList<FileArea>();
            List<String> allStages = getLifeCycleStages(factory);
            System.out.println("Lifecycle = " + allStages);
            int minArea = Integer.MAX_VALUE;
            for(FileArea area: areasList){
                int index = allStages.indexOf(area.getStageID());
                System.out.println("    Area= " + area.getName() +" stage= " + area.getStageID() + " index= " + index);
                if(index == minArea){
                    areasToDeploy.add(area);
                } else if(index < minArea){
                    areasToDeploy.clear();
                    areasToDeploy.add(area);
                    minArea = index;
                }
            }
            System.out.println("STAGE is not found, getting first lifecycle stage = " + allStages.get(minArea));
        } else {
            System.out.println("Filtering project areas: ");
            areasToDeploy = new ArrayList<FileArea>(areasList);
            for (Iterator<FileArea> i = areasToDeploy.iterator(); i.hasNext();) {
                FileArea area = i.next();
                if (!area.getStageID().equals(stage)) {
                    i.remove();
                    System.out.println("   Removing area: " + area.getName() + " , wrong stage = " + area.getStageID());
                } else if (areasSet != null && !areasSet.contains(area.getName())) {
                    i.remove();
                    System.out.println("   Removing area: " + area.getName() + " , is not in the list");
                } else {
                    System.out.println("   Accepted area: " + area.getName());
                }
            }
        }
        System.out.println("Deploying on areas: ");
        for(FileArea area: areasToDeploy){
            System.out.println("    " + area.getName());
        }
        deployDetails.setFileAreas(areasToDeploy);
        deployDetails.setProject(projectObj);
        deployDetails.setComment(comment + "[" + sraRequestID + "]");

        return baselineObj.submitDeployment(deployDetails);
    }

    /**
     * Method stub, not used in plugin. Just in case it will be needed in the future.
     */
    DimensionsResult rollbackBaseline(
            String product,
            String project,
            String baseline,
            String areas,
            String comment) {
        product = prepareDimCMParam(product);
        project = prepareDimCMParam(project);
        baseline = prepareDimCMParam(baseline);

        DimensionsObjectFactory factory = connection.getObjectFactory();
        Baseline baselineObj = getBaseline(factory, product, baseline);
        Project projectObj = getProjectIfExists(factory, product, project);

        DeploymentDetails d = new DeploymentDetails();
        d.setFileAreas(projectObj.getFileAreas());
        d.setProject(projectObj);
        d.setComment(comment);

        return baselineObj.submitRollback(d);
    }

    public DimensionsResult rollbackArea(String area, int version, String comment) {
        area = prepareDimCMParam(area);

        DimensionsObjectFactory factory = connection.getObjectFactory();

        String command = null;
        if (version == -1) {
            command = "SRAV " + cleverEscapeString(area) + " /COMMENT=" + escapeString(comment);
        } else {
            command = "SRAV " + cleverEscapeString(area + ";" + version) + " /COMMENT=" + escapeString(comment);
        }

        System.out.println("EXECUTING: " + command.toString());
        return factory.runCommand(command);
    }

    public DimensionsResult deliver(
            String dir,
            String product,
            String project,
            boolean add,
            boolean update,
            boolean delete,
            String attributes,
            String comment) {
        DimensionsObjectFactory factory = connection.getObjectFactory();

        StringBuilder command = new StringBuilder();
        command.append("DELIVER /USER_DIRECTORY=");
        command.append(cleverEscapeString(dir));

        command.append(" /WORKSET=");
        command.append(cleverEscapeString(product + ':' + project));

        if (!comment.isEmpty()) {
            command.append(" /COMMENT=");
            command.append(escapeString(comment));
        }
        command.append((add ? " /ADD" : " /NOADD"));
        command.append(delete ? " /DELETE" : " /NODELETE");
        command.append(update ? " /UPDATE" : " /NOUPDATE");

        if (!attributes.isEmpty()) {
            command.append(" /ATTRIBUTES=");
            command.append(formatAttributes(attributes));
        }

        System.out.println("EXECUTING: " + command.toString());
        return factory.runCommand(command.toString());
    }

    public DimensionsResult upload(
            String dir,
            String product,
            String project,
            String attributes,
            String comment,
            String description) {
        DimensionsObjectFactory factory = connection.getObjectFactory();

        StringBuilder command = new StringBuilder();
        command.append("UPLOAD /PERMS=KEEP /USER_DIRECTORY=");
        command.append(cleverEscapeString(dir));

        command.append(" /WORKSET=");
        command.append(cleverEscapeString(product + ':' + project));

        if (!comment.isEmpty()) {
            command.append(" /COMMENT=");
            command.append(escapeString(comment));
        }
        if (!description.isEmpty()) {
            command.append(" /DESCRIPTION=");
            command.append(escapeString(description));
        }

        if (!attributes.isEmpty()) {
            command.append(" /ATTRIBUTES=");
            command.append(formatAttributes(attributes));
        }

        System.out.println("EXECUTING: " + command.toString());
        return factory.runCommand(command.toString());
    }

    // =========================================================================
    // DimCM API helpers
    // =========================================================================

    class DeploymentInfo {
        final String entityName;
        final String jobName;
        String result;

        public DeploymentInfo(String entityName, String jobName) {
            this.entityName = entityName;
            this.jobName = jobName;
        }
    }

    @SuppressWarnings("unchecked")
    private DeploymentInfo getDeploymentStatus(String entityName, String sraRequestID) throws AdmException {

        List<AdmObject> objects = null;

        // Getting deployment history for entityName
        FilterImpl filter = new FilterImpl();
        filter.criteria().add(new FilterCriterion(AdmAttrNames.ID, entityName, FilterCriterion.EQUALS));

        DimensionsConnectionManager.registerThreadConnection(connection);
        try {
            DeploymentViewContext dvc = new DeploymentViewContext(AdmCmd.getCurRootObj(BaseDatabase.class).getAdmSpec());
            Cmd cmd = AdmCmd.getCmd(Relatable.QUERY_CHILDREN, dvc, DeploymentHistoryRecord.class);
            cmd.setAttrValue(CmdArguments.ATTRIBUTE_NAMES, DEPLOYMENT_ATTRIBUTES);
            cmd.setAttrValue(CmdArguments.DATA_RANGE_INFO, new RangeInfo(0, 9999));
            cmd.setAttrValue(CmdArguments.FILTER, filter);
            cmd.setAttrValue(CmdArguments.DEPLOYMENT_DATA_REQUESTED_MODE, Integer.valueOf(IDeploymentViewConstants.MODE_HISTORY));
            cmd.setAttrValue(CmdArguments.USE_CACHE, Boolean.FALSE);
            objects = (List<AdmObject>) cmd.execute();
        } finally {
            DimensionsConnectionManager.unregisterThreadConnection();
        }

        if (objects != null) {
            for (AdmObject obj : objects) {
                if (obj.getAttrValue(AdmAttrNames.COMMENT).toString().endsWith("[" + sraRequestID + "]")) {
                    DeploymentInfo info = new DeploymentInfo(entityName, (String) obj.getAttrValue(AdmAttrNames.JOB_NAME));
                    info.result = (String) obj.getAttrValue(AdmAttrNames.HISTORY_EVENT_RESULT);
                    return info;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private DeploymentInfo getDeploymentStatus(DeploymentInfo info) throws AdmException {

        List<AdmObject> objects = null;

        // Getting deployment history for entityName
        FilterImpl filter = new FilterImpl();
        filter.criteria().add(new FilterCriterion(AdmAttrNames.JOB_NAME, info.jobName, FilterCriterion.EQUALS));
        filter.criteria().add(new FilterCriterion(AdmAttrNames.ID, info.entityName, FilterCriterion.EQUALS));

        DimensionsConnectionManager.registerThreadConnection(connection);
        try {
            DeploymentViewContext dvc = new DeploymentViewContext(AdmCmd.getCurRootObj(BaseDatabase.class).getAdmSpec());
            Cmd cmd = AdmCmd.getCmd(Relatable.QUERY_CHILDREN, dvc, DeploymentHistoryRecord.class);
            cmd.setAttrValue(CmdArguments.ATTRIBUTE_NAMES, DEPLOYMENT_ATTRIBUTES);
            cmd.setAttrValue(CmdArguments.DATA_RANGE_INFO, new RangeInfo(0, 9999));
            cmd.setAttrValue(CmdArguments.FILTER, filter);
            cmd.setAttrValue(CmdArguments.DEPLOYMENT_DATA_REQUESTED_MODE, Integer.valueOf(IDeploymentViewConstants.MODE_HISTORY));
            cmd.setAttrValue(CmdArguments.USE_CACHE, Boolean.FALSE);
            objects = (List<AdmObject>) cmd.execute();
        } finally {
            DimensionsConnectionManager.unregisterThreadConnection();
        }

        if (objects != null) {
            info.result = (String) objects.get(0).getAttrValue(AdmAttrNames.HISTORY_EVENT_RESULT);
            return info;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Baseline getBaseline(DimensionsObjectFactory factory, String productName, String baselineName) {
        Filter filter = new Filter();
        Criterion cr = new Filter.Criterion(SystemAttributes.OBJECT_SPEC, productName + ':' + baselineName, Filter.Criterion.EQUALS);
        filter.criteria().add(cr);

        List<Baseline> list = factory.getBaselines(filter);
        if (list.isEmpty()) {
            throw new RuntimeException("Error: the specified baseline does not exist - " + productName + ':' + baselineName);
        }
        return list.get(0);
    }

    private static Project getProjectIfExists(DimensionsObjectFactory factory, String product, String project) {
        return getProjectIfExists(factory, product + ":" + project);
    }

    private static Project getProjectIfExists(DimensionsObjectFactory factory, String projectFullName) {
        try {
            Project p = factory.getProject(projectFullName);
            return p;
        } catch (DimensionsRuntimeException e) {
            throw new RuntimeException("Error: the specified Project is not found!", e);
        }
    }

    private static Project getProjectIfExists(DimensionsObjectFactory factory, String projectFullName, boolean throwIfNull) {
        try {
            Project p = factory.getProject(projectFullName);
            return p;
        } catch (DimensionsRuntimeException e) {
            if (throwIfNull) {
                throw new RuntimeException("Error: the specified Project is not found!", e);
            }
            return null;
        }
    }

    private static boolean isProductExists(DimensionsObjectFactory factory, String productName) {
        @SuppressWarnings("unchecked")
        List<Product> list = factory.getBaseDatabase().getProducts();
        for (Product product : list) {
            if (product.getName().equals(productName)) {
                return true;
            }
        }
        return false;
    }

    private static void productShouldExist(DimensionsObjectFactory factory, String productName) {
        if (!isProductExists(factory, productName)) {
            throw new RuntimeException("Error: the specified product name - " + productName + " - does not exist");
        }
    }

    private static void stageShouldExist(DimensionsObjectFactory factory, String stage) {
        BuildStages bs = factory.getBaseDatabaseAdmin().getBuildStages();

        boolean doesStageExist = false;
        for (@SuppressWarnings("unchecked")
             Iterator<String> i = bs.iterator(); i.hasNext();) {
            if (i.next().equals(stage)) {
                doesStageExist = true;
            }
        }
        if (!doesStageExist) {
            throw new RuntimeException("Error: the specified stage name - " + stage + " - does not exist");
        }
    }

    // How to get attributes. Maybe useful - not to forget
    private static String getBaselineCurrentStage(Baseline baseline) {
        baseline.queryAttribute(SystemAttributes.STAGE);
        return (String) baseline.getAttribute(SystemAttributes.STAGE);
        // Not sure what type. Maybe smth like Project is returned
    }


    private static List<String> getLifeCycleStages(DimensionsObjectFactory factory) {
        List<String> res = new ArrayList<String>();
//      More clear version, but there is bug in dimensions java API - BuildStages loses stages` order
//        List<String> res2 = new ArrayList<String>();
//        BuildStages bs = factory.getBaseDatabaseAdmin().getBuildStages();
//        for (Iterator<String> i = bs.iterator(); i.hasNext(); ){
//            res2.add(i.next());
//        }
        Lifecycle lc = factory.getBaseDatabase().getLifecycle(DIMCM_DEFAULT_LIFECYCLE);
        for (Iterator<?> i = lc.getNormalStates().iterator(); i.hasNext(); ){
            res.add(i.next().toString());
        }
        return res;
    }



    // ========================================================================
    // Simple Helper methods
    // ========================================================================

    private static String prepareDimCMParam(String param) {
        if (param == null) {
            return null;
        }
        return param.toUpperCase();
    }

    private static String escapeString(String str) {
        return "\"" + str + "\"";
    }

    private static String cleverEscapeString(String str) {
        if(str.contains(" ")){
            return "\"" + str + "\"";
        }
        return str;
    }

    private static String formatAttributes(String str) {
        return "(" + trimTextAreaLines(str).replace('\n', ',') + ")";
    }

    private static boolean isCausedBy(Throwable e, Class<? extends Throwable> cl) {
        Throwable cause;
        while ((cause = e.getCause()) != null) {
            if (cl.isAssignableFrom(cause.getClass())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused") //Debug utility
    private static void viewAttributes(DimensionsArObject obj, int... attr_id) {
        System.out.println("Attributes for " + obj.getName());
        for (int i : attr_id) {
            System.out.println(i);
            obj.queryAttribute(i);
            System.out.println(obj.getAttribute(i));
        }
    }

    /**
     * Replaces ; with \n.
     * Removes spaces at beginning and ending of each line.
     * Removes duplicating \n characters.
     */
    private static String trimTextAreaLines(String str){
        return str.replaceAll(';', '\n').replaceAll('\\s*(\n|^|$)\\s*', '$1').replaceAll('\\s*\n\\s*\n*\\s*', '\n');
    }

    private static String cutProductName(String str){
        int pos = str.indexOf(":");
        if (pos > 0){
            return str.substring(pos + 1);
        }
        return str;
    }

    // ========================================================================
    // Public Helper methods - for using in steps
    // ========================================================================
    /**
     * For catching mistypings in plugin.xml
     */
    public static String notNull(Properties prop, String paramName) {
        String res = prop.getProperty(paramName);
        if (res == null) {
            throw new RuntimeException("Parameter " + paramName + " is missing!");
        }
        return res;
    }

    public static boolean notNullBoolean(Properties prop, String paramName) {
        String res = prop.getProperty(paramName);
        if (res == null) {
            throw new RuntimeException("Parameter " + paramName + " is missing!");
        }
        // System.out.println("notNullBoolean = [" + res + "]" + Boolean.parseBoolean(res));
        return Boolean.parseBoolean(res);
    }

    public static String optional(Properties prop, String paramName, String defValue) {
        String res = prop.getProperty(paramName);

        return res != null ? res : defValue;
    }

    public static int optionalInt(Properties prop, String paramName, int defValue) {
        String res = prop.getProperty(paramName);
        if (res == null || res.isEmpty()) {
            return defValue;
        }
        return Integer.parseInt(res);
    }

    public static List<Properties> convert(List<String> input, String rlm_key) {
        List<Properties> result = new ArrayList<Properties>();
        for (String str : input) {
            Properties prop = new Properties();
            prop.setProperty(rlm_key, str);
            result.add(prop);
        }
        return result;
    }

    public int waitForDeployment(String entityName, String sraRequestID, int timeout) {
        if (timeout == -1) {
            timeout = MILISECONDS_IN_24_HOURS;
        }
        DeploymentInfo info = null;
        long start = System.currentTimeMillis();
        String prevStatus = null;
        while (info == null || !deploymentStatusIsFinished(info)) {
            if (System.currentTimeMillis() - start > timeout) {
                throw new RuntimeException("Stopped by timeout!");
            }
            try {
                if (info == null) {
                    // First time - looking for entity with sraRequestID in the comment
                    info = getDeploymentStatus(entityName, sraRequestID);
                    if(info == null){ // no deployment was found - all areas were skipped
                        System.out.println("No deployment was found, all areas were skipped.");
                        break
                    }
                } else {
                    // Then - getting status by DeploymentInfo.JOB_NAME, returned from the first request
                    info = getDeploymentStatus(info);
                }

                if(!info.result.equals(prevStatus)){
                    System.out.println("STATUS == " + getStatusStr(info.result));
                    prevStatus = info.result
                }
                Thread.sleep(200);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (AdmException e) {
                e.printStackTrace();
            }
        }
        if(info == null || DIMCM_DEPLOYMENT_STATUS__OK.equals(info.result)){
            return 0; // OK exit code
        } else {
            return 1; // ERROR exit code
        }
    }

    /**
     * 0 = Submitted
     * 1 = Executing
     * 2 = Succeeded
     * 3 = Failed
     * if status >= 2 - deployment is finished (successfully or not)
     * @param info
     * @return
     */
    private boolean deploymentStatusIsFinished(DeploymentInfo info){
//        if (info.result == null){ return false; }
        try{
            return Integer.parseInt(info.result) >= 2;
        } catch (Exception e){
            System.out.println("WARNING: unexpected deployment status =" + info.result);
            return false;
        }
    }
    private String getStatusStr(String status){
        switch (status){
            case '0': return 'Submitted'
            case '1': return 'Executing'
            case '2': return 'Succeeded'
            case '3': return 'Failed'
        }
    }
}

