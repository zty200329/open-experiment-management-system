package com.swpu.uchain.openexperiment.service.impl;

import com.swpu.uchain.openexperiment.DTO.ConclusionDTO;
import com.swpu.uchain.openexperiment.DTO.OperationRecord;
import com.swpu.uchain.openexperiment.DTO.ProjectHistoryInfo;
import com.swpu.uchain.openexperiment.VO.limit.AmountAndTypeVO;
import com.swpu.uchain.openexperiment.VO.project.*;
import com.swpu.uchain.openexperiment.VO.user.UserMemberVO;
import com.swpu.uchain.openexperiment.config.UploadConfig;
import com.swpu.uchain.openexperiment.form.check.KeyProjectCheck;
import com.swpu.uchain.openexperiment.mapper.*;
import com.swpu.uchain.openexperiment.domain.*;
import com.swpu.uchain.openexperiment.enums.*;
import com.swpu.uchain.openexperiment.exception.GlobalException;
import com.swpu.uchain.openexperiment.form.member.MemberQueryCondition;
import com.swpu.uchain.openexperiment.form.project.*;
import com.swpu.uchain.openexperiment.form.query.QueryConditionForm;
import com.swpu.uchain.openexperiment.form.query.HistoryQueryProjectInfo;
import com.swpu.uchain.openexperiment.redis.RedisService;
import com.swpu.uchain.openexperiment.redis.key.ProjectGroupKey;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.service.*;
import com.swpu.uchain.openexperiment.util.ConvertUtil;
import com.swpu.uchain.openexperiment.util.IPUtil;
import com.swpu.uchain.openexperiment.util.SerialNumberUtil;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;


/**
 * @author panghu
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private UserService userService;
    private ProjectGroupMapper projectGroupMapper;
    private RedisService redisService;
    private UserProjectService userProjectService;
    private ProjectFileService projectFileService;
    private FundsService fundsService;
    private UploadConfig uploadConfig;
    private ConvertUtil convertUtil;
    private GetUserService getUserService;
    private UserProjectGroupMapper userProjectGroupMapper;
    private RoleMapper roleMapper;
    private OperationRecordMapper recordMapper;
    private UserMapper userMapper;
    private KeyProjectStatusMapper keyProjectStatusMapper;
    private ProjectFileMapper projectFileMapper;
    private TimeLimitService timeLimitService;
    private AmountLimitMapper amountLimitMapper;

    @Autowired
    public ProjectServiceImpl(UserService userService, ProjectGroupMapper projectGroupMapper,
                              RedisService redisService, UserProjectService userProjectService,
                              ProjectFileService projectFileService, FundsService fundsService,
                              UploadConfig uploadConfig,
                              ConvertUtil convertUtil, GetUserService getUserService,
                              OperationRecordMapper recordMapper,
                              RoleMapper roleMapper, AmountLimitMapper amountLimitMapper,
                              UserProjectGroupMapper userProjectGroupMapper, UserMapper userMapper,
                              KeyProjectStatusMapper keyProjectStatusMapper, ProjectFileMapper projectFileMapper,
                              TimeLimitService timeLimitService) {
        this.userService = userService;
        this.projectGroupMapper = projectGroupMapper;
        this.redisService = redisService;
        this.userProjectService = userProjectService;
        this.projectFileService = projectFileService;
        this.fundsService = fundsService;
        this.uploadConfig = uploadConfig;
        this.convertUtil = convertUtil;
        this.getUserService = getUserService;
        this.userProjectGroupMapper = userProjectGroupMapper;
        this.recordMapper = recordMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.keyProjectStatusMapper = keyProjectStatusMapper;
        this.projectFileMapper = projectFileMapper;
        this.timeLimitService = timeLimitService;
        this.amountLimitMapper = amountLimitMapper;
    }

    @Override
    public boolean insert(ProjectGroup projectGroup) {
        return projectGroupMapper.insert(projectGroup) == 1;
    }

    @Override
    public boolean update(ProjectGroup projectGroup) {
        projectGroup.setUpdateTime(new Date());
        return projectGroupMapper.updateByPrimaryKey(projectGroup) == 1;
    }

    @Override
    public void delete(Long projectGroupId) {
        redisService.delete(ProjectGroupKey.getByProjectGroupId, projectGroupId + "");
        projectGroupMapper.deleteByPrimaryKey(projectGroupId);
        //删除项目相关的成员信息
        userProjectService.deleteByProjectGroupId(projectGroupId);
        //TODO,删除所有的关系模块,文件,答辩小组,资金

    }

    @Override
    public ProjectGroup selectByProjectGroupId(Long projectGroupId) {
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGroupId);
        //重点项目状态表中不为空，设置值为重点项目状态
        if (projectGroup.getWhetherCommitKeyApply() != null) {
            projectGroup.setStatus(projectGroup.getWhetherCommitKeyApply());
        }
        return projectGroupMapper.selectByPrimaryKey(projectGroupId);
    }

    /**
     * 添加项目组
     *
     * @param projectGroup
     * @return
     */
    private Result addProjectGroup(ProjectGroup projectGroup) {
        projectGroup.setCreateTime(new Date());
        projectGroup.setUpdateTime(new Date());
        if (insert(projectGroup)) {
            return Result.success();
        }
        return Result.error(CodeMsg.ADD_ERROR);
    }

    @Override
    public List<ProjectGroup> selectByUserIdAndProjectStatus(Long userId, Integer projectStatus, Integer joinStatus) {
        //获取当前用户参与的所有项目
        return projectGroupMapper.selectByUserIdAndStatus(userId, projectStatus, joinStatus);
    }

    /**
     * 指导教师填写申请立项书
     *
     * @param form 申请立项表单
     * @return 申请立项操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result applyCreateProject(CreateProjectApplyForm form) {
        //验证时间限制
        timeLimitService.validTime(TimeLimitType.DECLARE_LIMIT);
        User currentUser = getUserService.getCurrentUser();
        Integer college = currentUser.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //验证项目是否达到申请上限
        AmountAndTypeVO amountAndTypeVO = amountLimitMapper.getAmountAndTypeVOByCollegeAndProjectType(null, form.getProjectType(), RoleType.MENTOR.getValue());
        if (amountAndTypeVO != null) {
            Integer maxAmount = amountAndTypeVO.getMaxAmount();
            Integer currentCount = userProjectGroupMapper.geCountOfAppliedProject(Long.valueOf(currentUser.getCode()), form.getProjectType());
            if (maxAmount >= currentCount) {
                return Result.error(CodeMsg.MAXIMUM_APPLICATION);
            }
        }


        //判断用户类型
        if (currentUser.getUserType().intValue() == UserType.STUDENT.getValue()) {
            Result.error(CodeMsg.STUDENT_CANT_APPLY);
        }

        //开放选题时,不进行学生选择
        if (form.getIsOpenTopic().equals(OpenTopicType.OPEN_TOPIC_ALL.getValue()) && form.getStuCodes() != null) {
            throw new GlobalException(CodeMsg.TOPIC_IS_NOT_OPEN);
        }

        //时间设置出错
        if (form.getStartTime().getTime() >= form.getEndTime().getTime()) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }

        ProjectGroup projectGroup = new ProjectGroup();
        BeanUtils.copyProperties(form, projectGroup);
        projectGroup.setStatus(ProjectStatus.DECLARE.getValue());
        //设置申请人
        projectGroup.setCreatorId(Long.valueOf(currentUser.getCode()));
        projectGroup.setSubordinateCollege(college);
        //插入数据
        Result result = addProjectGroup(projectGroup);
        if (result.getCode() != 0) {
            throw new GlobalException(CodeMsg.ADD_PROJECT_GROUP_ERROR);
        }

        //设置项目创建编号
        String serialNumber = projectGroupMapper.getMaxSerialNumberByCollege(college);
        //计算编号并在数据库中插入编号
        projectGroupMapper.updateProjectSerialNumber(projectGroup.getId(), SerialNumberUtil.getSerialNumberOfProject(college, ProjectType.KEY.getValue(), serialNumber));


//        String[] teacherCodes = form.getTeacherCodes();

        //在指导教师中添加当前用户
//        String[] teacherArray = Arrays.copyOf(teacherCodes,teacherCodes.length+1);
//        teacherArray[teacherArray.length-1] = currentUser.getCode();

        String[] teacherArray = new String[1];
        teacherArray[0] = currentUser.getCode();

        String[] stuCodes = form.getStuCodes();
        userProjectService.addStuAndTeacherJoin(stuCodes, teacherArray, projectGroup.getId());
        //记录申请信息
        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setRelatedId(projectGroup.getId());
        operationRecord.setOperationType(OperationType.REPORT.getValue());
        operationRecord.setOperationUnit(OperationUnit.MENTOR.getValue());
        operationRecord.setOperationExecutorId(Long.valueOf(currentUser.getCode()));
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result applyUpdateProject(UpdateProjectApplyForm updateProjectApplyForm) {

        //验证时间限制
        timeLimitService.validTime(TimeLimitType.DECLARE_LIMIT);

        ProjectGroup projectGroup = selectByProjectGroupId(updateProjectApplyForm.getProjectGroupId());
        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        User currentUser = getUserService.getCurrentUser();
        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(updateProjectApplyForm.getProjectGroupId(), Long.valueOf(currentUser.getCode()));
        if (userProjectGroup == null) {
            return Result.error(CodeMsg.USER_NOT_IN_GROUP);
        }
        //状态不允许修改
        if (!projectGroup.getStatus().equals(ProjectStatus.DECLARE.getValue()) && !projectGroup.getStatus().equals(ProjectStatus.REJECT_MODIFY.getValue())) {
            return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
        }
        //更新项目组基本信息
        BeanUtils.copyProperties(updateProjectApplyForm, projectGroup);
        update(projectGroup);
        userProjectService.deleteByProjectGroupId(projectGroup.getId());
        String[] stuCodes = null;
        String[] teacherCodes = null;

        //更新成员信息--不需要
//        if (updateProjectApplyForm.getStuCodes() != null ) {
//            stuCodes = new String[updateProjectApplyForm.getStuCodes().length];
//            for (int i = 0; i < updateProjectApplyForm.getStuCodes().length; i++) {
//                stuCodes[i] = updateProjectApplyForm.getStuCodes()[i].toString();
//            }
//        }
//        if (updateProjectApplyForm.getTeacherCodes() != null) {
//            teacherCodes = new String[updateProjectApplyForm.getTeacherCodes().length];
//            for (int i = 0; i < updateProjectApplyForm.getTeacherCodes().length; i++) {
//                teacherCodes[i] = updateProjectApplyForm.getTeacherCodes()[i].toString();
//            }
//        }
//
//        userProjectService.addStuAndTeacherJoin(stuCodes, teacherCodes, projectGroup.getId());
        //修改项目状态,重新开始申报
//        updateProjectStatus(projectGroup.getId(), ProjectStatus.ESTABLISH.getValue());

        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setRelatedId(updateProjectApplyForm.getProjectGroupId());
        operationRecord.setOperationType(OperationType.MODIFY.getValue());
        operationRecord.setOperationUnit(OperationUnit.MENTOR.getValue());
        //设置执行人
        setOperationExecutor(operationRecord);
        recordMapper.insert(operationRecord);
        return Result.success();
    }

    @Override
    public Result getCurrentUserProjects(Integer projectStatus, Integer joinStatus) {
        User currentUser = getUserService.getCurrentUser();


        List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), projectStatus, joinStatus);
        //设置当前用户的所有项目VO
        List<MyProjectVO> projectVOS = new ArrayList<>();
        for (ProjectGroup projectGroup : projectGroups) {
            Integer numberOfSelectedStu = userProjectGroupMapper.selectStuCount(projectGroup.getId(), null);
            MyProjectVO myProjectVO = new MyProjectVO();
            Integer status = keyProjectStatusMapper.getStatusByProjectId(projectGroup.getId());
            if (status != null) {
                myProjectVO.setStatus(status);
            }
            BeanUtils.copyProperties(projectGroup, myProjectVO);
            myProjectVO.setMemberRole(userProjectGroupMapper.selectByProjectIdAndUserId(projectGroup.getId(), Long.valueOf(currentUser.getCode())).getMemberRole());
            myProjectVO.setId(projectGroup.getId());
            myProjectVO.setNumberOfTheSelected(numberOfSelectedStu);
            myProjectVO.setProjectDetails(getProjectDetails(projectGroup));
            projectVOS.add(myProjectVO);
        }
        return Result.success(projectVOS);
    }

    private ProjectDetails getProjectDetails(ProjectGroup projectGroup) {
        ProjectDetails projectDetails = new ProjectDetails();
        projectDetails.setLabName(projectGroup.getLabName());
        projectDetails.setAddress(projectGroup.getAddress());
        //设置创建人,即项目负责人
        User user = userService.selectByUserId(projectGroup.getCreatorId());
        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(
                projectGroup.getId(),
                Long.valueOf(user.getCode()));
        projectDetails.setCreator(new UserMemberVO(
                Long.valueOf(user.getCode()),
                user.getRealName(),
                userProjectGroup.getMemberRole(),null,null));
        //设置项目的成员信息
        List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(projectGroup.getId());
        List<UserMemberVO> members = new ArrayList<>();
        for (UserProjectGroup userProject : userProjectGroups) {
            User member = userService.selectByUserId(userProject.getUserId());
            //设置项目组组长
            if (userProject.getMemberRole().intValue() == MemberRole.PROJECT_GROUP_LEADER.getValue()) {
                projectDetails.setLeader(new UserMemberVO(
                        member.getId(),
                        member.getRealName(),
                        userProject.getMemberRole(),null,null));
            }
            UserMemberVO userMemberVO = new UserMemberVO();
            userMemberVO.setUserId(member.getId());
            userMemberVO.setUserName(member.getRealName());
            userMemberVO.setMemberRole(userProject.getMemberRole());
            members.add(userMemberVO);
        }
        projectDetails.setMembers(members);
        //设置项目资金详情
        List<Funds> fundsDetails = fundsService.getFundsDetails(projectGroup.getId());
        int applyAmount = 0, agreeAmount = 0;
        for (Funds fundsDetail : fundsDetails) {
            applyAmount += fundsDetail.getAmount();
            if (fundsDetail.getStatus().intValue() == FundsStatus.AGREED.getValue()) {
                agreeAmount += fundsDetail.getAmount();
            }
        }
        projectDetails.setTotalApplyFundsAmount(applyAmount);
        projectDetails.setTotalAgreeFundsAmount(agreeAmount);
        projectDetails.setFundsDetails(fundsDetails);
        List<ProjectFile> projectFiles = projectFileService.getProjectAllFiles(projectGroup.getId());
        projectDetails.setProjectFiles(projectFiles);
        return projectDetails;
    }

    @Override
    public Result agreeJoin(JoinForm[] joinForm) {
        for (JoinForm form : joinForm) {
            User user = userService.selectByUserId(form.getUserId());
            if (user == null) {
                return Result.error(CodeMsg.USER_NO_EXIST);
            }
            ProjectGroup projectGroup = selectByProjectGroupId(form.getProjectGroupId());
            if (projectGroup == null) {
                return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
            }
            UserProjectGroup userProjectGroup = userProjectService
                    .selectByProjectGroupIdAndUserId(
                            form.getProjectGroupId(),
                            Long.valueOf(user.getCode()));
            //未申请用户不得加入
            if (userProjectGroup == null) {
                return Result.error(CodeMsg.USER_NOT_APPLYING);
            }
            //已加入用户不能再次加入
            if (userProjectGroup.getStatus().intValue() == JoinStatus.JOINED.getValue()) {
                return Result.error(CodeMsg.USER_HAD_JOINED);
            }
            //一倍拒绝的用户无法再次加入该项目
            if (userProjectGroup.getStatus().intValue() == JoinStatus.UN_PASS.getValue()) {
                return Result.error(CodeMsg.USER_HAD_BEEN_REJECTED);
            }
            userProjectGroup.setStatus(JoinStatus.JOINED.getValue());
            if (!userProjectService.update(userProjectGroup)) {
                return Result.error(CodeMsg.UPDATE_ERROR);
            }
        }
        return Result.success();
    }

    private Result updateProjectStatus(Long projectGroupId, Integer projectStatus) {
        ProjectGroup projectGroup = selectByProjectGroupId(projectGroupId);
        if (projectGroup == null) {
            throw new GlobalException(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        projectGroup.setStatus(projectStatus);

        //更新状态
        if (update(projectGroup)) {
            return Result.success();
        }
        return Result.error(CodeMsg.UPDATE_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result agreeEstablish(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.AGREE, OperationUnit.FUNCTIONAL_DEPARTMENT);
    }

    @Override
    public Result agreeIntermediateInspectionProject(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.OFFLINE_CHECK, OperationUnit.FUNCTIONAL_DEPARTMENT);
    }

    @Override
    public Result agreeToBeConcludingProject(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.CONCLUSION, OperationUnit.FUNCTIONAL_DEPARTMENT);
    }

    private Result setProjectStatusAndRecord(List<ProjectCheckForm> list, OperationType operationType, OperationUnit operationUnit) {
        List<OperationRecord> operationRecordS = new LinkedList<>();
        for (ProjectCheckForm projectCheckForm : list) {
            Result result = updateProjectStatus(projectCheckForm.getProjectId(), ProjectStatus.ESTABLISH.getValue());
            if (result.getCode() != 0) {
                throw new GlobalException(CodeMsg.UPDATE_ERROR);
            }
            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationReason(projectCheckForm.getReason());
            operationRecord.setRelatedId(projectCheckForm.getProjectId());
            operationRecordS.add(operationRecord);
            setOperationExecutor(operationRecord);
        }
        recordMapper.multiInsert(operationRecordS);
        return Result.success();
    }

    @Override
    public Result getApplyForm(Long projectGroupId) {

        ProjectGroup projectGroup = selectByProjectGroupId(projectGroupId);
        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        List<User> users = userService.selectProjectJoinedUsers(projectGroupId);
        if (projectGroup.getProjectType().intValue() == ProjectType.KEY.getValue()) {
            ApplyKeyFormInfoVO applyKeyFormInfoVO = convertUtil.addUserDetailVO(users, ApplyKeyFormInfoVO.class);
            applyKeyFormInfoVO.setFundsDetails(fundsService.getFundsDetails(projectGroupId));
            applyKeyFormInfoVO.setCreatorName(userMapper.selectByUserCode(String.valueOf(projectGroup.getCreatorId())).getRealName());
            BeanUtils.copyProperties(projectGroup, applyKeyFormInfoVO);
            applyKeyFormInfoVO.setId(projectGroup.getId());
            return Result.success(applyKeyFormInfoVO);
        } else {
            ApplyGeneralFormInfoVO applyGeneralFormInfoVO = convertUtil.addUserDetailVO(users, ApplyGeneralFormInfoVO.class);
            BeanUtils.copyProperties(projectGroup, applyGeneralFormInfoVO);
            applyGeneralFormInfoVO.setId(projectGroup.getId());
            applyGeneralFormInfoVO.setCreatorName(userMapper.selectByUserCode(String.valueOf(projectGroup.getCreatorId())).getRealName());
            return Result.success(applyGeneralFormInfoVO);
        }
    }

//    @Override
//    public Result appendCreateApply(AppendApplyForm appendApplyForm) {
//        User currentUser = getUserService.getCurrentUser();
//        //获取用户所在的用户项目组信息
//        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(
//                appendApplyForm.getProjectGroupId(), Long.valueOf(currentUser.getCode());
//        if (userProjectGroup == null) {
//            return Result.error(CodeMsg.USER_NOT_IN_GROUP);
//        }
//
//        if (userProjectGroup.getStatus() < 5){
//            throw new GlobalException(CodeMsg.FUNDS_NOT_EXIST);
//        }
//
//        //拒绝普通用户进行该项操作
//        if (userProjectGroup.getMemberRole().intValue() == MemberRole.NORMAL_MEMBER.getValue()) {
//            Result.error(CodeMsg.PERMISSION_DENNY);
//        }
//        FundForm[] fundsForms = appendApplyForm.getFundForms();
//        for (FundForm fundsForm : fundsForms) {
//            //资金id不为空进行更新操作
//            if (fundsForm.getFundsId() != null) {
//                Funds funds = fundsService.selectById(fundsForm.getFundsId());
//
//                if (funds == null) {
//                    return Result.error(CodeMsg.FUNDS_NOT_EXIST);
//                }
//                //申请通过的资金无进行更新操作
//                if (funds.getStatus().intValue() == FundsStatus.AGREED.getValue()) {
//                    return Result.error(CodeMsg.FUNDS_AGREE_CANT_CHANGE);
//                }
//                BeanUtils.copyProperties(fundsForm, funds);
//                funds.setUpdateTime(new Date());
//                if (!fundsService.update(funds)) {
//                    return Result.error(CodeMsg.UPDATE_ERROR);
//                }
//            } else {
//                //添加资金信息
//                Funds funds = new Funds();
//                BeanUtils.copyProperties(fundsForm, funds);
//                funds.setProjectGroupId(appendApplyForm.getProjectGroupId());
//                funds.setApplicantId(Long.valueOf(currentUser.getCode());
//                funds.setStatus(FundsStatus.APPLYING.getValue());
//                funds.setCreateTime(new Date());
//                funds.setUpdateTime(new Date());
//                if (!fundsService.insert(funds)) {
//                    return Result.error(CodeMsg.ADD_ERROR);
//                }
//            }
//        }
//        return Result.success();
//    }

    @Override
    public Result getPendingApprovalProjectByLabAdministrator() {
        //TODO 身份验证
        return getCheckInfo(ProjectStatus.DECLARE);
    }

    @Override
    public Result getPendingApprovalProjectBySecondaryUnit() {
        //TODO 身份验证

        return getCheckInfo(ProjectStatus.LAB_ALLOWED_AND_REPORTED);
    }

    @Override
    public Result getPendingApprovalProjectByFunctionalDepartment() {
        //TODO 身份验证
        return getCheckInfo(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED);
    }

    @Override
    public Result getToBeConcludingProject() {
        //TODO 身份验证
        return getCheckInfo(ProjectStatus.ESTABLISH);
    }

    @Override
    public Result getIntermediateInspectionProject() {
        //TODO 身份验证
        return getCheckInfo(ProjectStatus.MID_TERM_INSPECTION);
    }

    private Result getReportInfo(Integer role) {
        Integer projectStatus;
        switch (role) {
            //二级部门(学院领导)
            case 5:
                projectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                break;
            //实验室主任
            case 4:
                projectStatus = ProjectStatus.LAB_ALLOWED.getValue();
                break;
            default:
                throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }
        //获取待上报的普通项目
        List<CheckProjectVO> checkProjectVOs = projectGroupMapper.selectApplyOrderByTime(projectStatus, ProjectType.GENERAL.getValue());
        for (CheckProjectVO checkProjectVO : checkProjectVOs) {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(checkProjectVO.getId());
            List<UserMemberVO> guidanceTeachers = new ArrayList<>();
            for (UserProjectGroup userProjectGroup : userProjectGroups
            ) {
                UserMemberVO userMemberVO = new UserMemberVO();
                userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
                userMemberVO.setUserId(userProjectGroup.getUserId());
                userMemberVO.setUserName(userMapper.selectByUserCode(String.valueOf(userProjectGroup.getUserId())).getRealName());
                guidanceTeachers.add(userMemberVO);
            }
            checkProjectVO.setGuidanceTeachers(guidanceTeachers);
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
        }
        return Result.success(checkProjectVOs);
    }

    private Result getCheckInfo(ProjectStatus projectStatus) {
        Integer status = projectStatus.getValue();
        Integer projectType = ProjectType.GENERAL.getValue();
        //如果是实验室进行审批，则返回所有项目
        if (projectStatus == ProjectStatus.DECLARE) {
            projectType = null;
        }
        List<CheckProjectVO> checkProjectVOs = projectGroupMapper.selectApplyOrderByTime(status, projectType);
        for (CheckProjectVO checkProjectVO : checkProjectVOs) {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(checkProjectVO.getId());
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
            List<UserMemberVO> guidanceTeachers = new ArrayList<>();
            List<UserMemberVO> memberStudents = new ArrayList<>();
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                UserMemberVO userMemberVO = new UserMemberVO();
                User user = userService.selectByUserId(userProjectGroup.getUserId());
                userMemberVO.setUserId(Long.valueOf(user.getCode()));
                userMemberVO.setUserName(user.getRealName());
                userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
                //设置负责人(项目组长)
                switch (userProjectGroup.getMemberRole()) {
                    case 1:
                        guidanceTeachers.add(userMemberVO);
                        break;
                    case 2:
                        checkProjectVO.setGroupLeaderPhone(user.getMobilePhone());
                        memberStudents.add(userMemberVO);
                        break;
                    case 3:
                        memberStudents.add(userMemberVO);
                        break;
                    default:
                        break;
                }
                //设置立项申请文件的id
                ProjectFile applyProjectFile = projectFileService.getAimNameProjectFile(
                        userProjectGroup.getProjectGroupId(),
                        uploadConfig.getApplyFileName());
                if (applyProjectFile != null) {
                    checkProjectVO.setApplyFileId(applyProjectFile.getId());
                }
                checkProjectVO.setMemberStudents(memberStudents);
                checkProjectVO.setGuidanceTeachers(guidanceTeachers);
            }
        }
        return Result.success(checkProjectVOs);
    }

    private boolean checkProjectStatus(List<Long> projectIdList, Integer status) {
        int count = projectGroupMapper.selectSpecifiedProjectList(projectIdList, status);
        return count == projectIdList.size();
    }


    @Transactional(rollbackFor = GlobalException.class)
    public Result reportToHigherUnit(List<Long> projectGroupIdList, ProjectStatus
            rightProjectStatus, OperationUnit operationUnit) {
        List<OperationRecord> list = new LinkedList<>();
        if (!checkProjectStatus(projectGroupIdList, rightProjectStatus.getValue())) {
            throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }

        //存入操作历史
        for (Long projectId : projectGroupIdList
        ) {
            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setRelatedId(projectId);
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationType(OperationType.REPORT.getValue());
            setOperationExecutor(operationRecord);

            list.add(operationRecord);
        }

        recordMapper.multiInsert(list);
        ProjectStatus newProjectStatus;
        if (rightProjectStatus == ProjectStatus.LAB_ALLOWED) {
            newProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED;
            //如果是二级单位操作
        } else {
            newProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED;
        }
        projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, newProjectStatus.getValue());
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = GlobalException.class)
    public Result reportToCollegeLeader(List<Long> projectGroupIdList) {
        //时间限制验证
        timeLimitService.validTime(TimeLimitType.LAB_REPORT_LIMIT);
        return reportToHigherUnit(projectGroupIdList, ProjectStatus.LAB_ALLOWED, OperationUnit.LAB_ADMINISTRATOR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result reportToFunctionalDepartment(List<Long> projectGroupIdList) {
        //数量限制
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //生成项目编号
        for (Long id : projectGroupIdList) {
            String serialNumber = projectGroupMapper.selectByPrimaryKey(id).getSerialNumber();
            //计算编号并在数据库中插入编号
            projectGroupMapper.updateProjectSerialNumber(id, SerialNumberUtil.getSerialNumberOfProject(college, ProjectType.KEY.getValue(), serialNumber));
        }


        AmountAndTypeVO amountAndTypeVO = amountLimitMapper.getAmountAndTypeVOByCollegeAndProjectType(college, ProjectType.GENERAL.getValue(), RoleType.SECONDARY_UNIT.getValue());
        Integer currentAmount = projectGroupMapper.getCountOfSpecifiedStatusAndProjectProject(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue(), college);
        if (currentAmount + projectGroupIdList.size() > amountAndTypeVO.getMaxAmount()) {
            throw new GlobalException(CodeMsg.PROJECT_AMOUNT_LIMIT);
        }

        //时间限制
        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_REPORT_LIMIT);
        return reportToHigherUnit(projectGroupIdList, ProjectStatus.SECONDARY_UNIT_ALLOWED, OperationUnit.SECONDARY_UNIT);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result ensureOrNotModify(ConfirmForm confirmForm) {
        Integer result = confirmForm.getResult();
        Long projectId = confirmForm.getProjectId();
        //确认修改
        if (recordMapper.selectDesignatedTypeListByRelatedIdAndType
                (OperationType.AGREE.getValue(), projectId).size() == 0) {
            throw new GlobalException(CodeMsg.PROJECT_NOT_MODIFY_BY_FUNCTION_DEPARTMENT);
        }
        //如果项目通过
        if (result.equals(OperationType.AGREE.getValue())) {
            updateProjectStatus(projectId, ProjectStatus.ESTABLISH.getValue());
        } else if (result.equals(OperationType.REJECT.getValue())) {
            updateProjectStatus(projectId, ProjectStatus.ESTABLISH_FAILED.getValue());
        }
        return Result.success();
    }

    @Override
    public Result getProjectDetailById(Long projectId) {
        List<ProjectHistoryInfo> list = recordMapper.selectAllByProjectId(projectId);
        return Result.success(list);
    }

    @Override
    public Result approveProjectApplyByLabAdministrator(List<ProjectCheckForm> list) {
        //时间限制验证
        timeLimitService.validTime(TimeLimitType.LAB_CHECK_LIMIT);
        return approveProjectApply(list, RoleType.LAB_ADMINISTRATOR.getValue());
    }


    @Override
    public Result approveProjectApplyBySecondaryUnit(List<ProjectCheckForm> list) {
        //时间限制验证
        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_CHECK_LIMIT);

        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        return approveProjectApply(list, RoleType.SECONDARY_UNIT.getValue());
    }

    @Override
    public Result conditionallyQueryOfProject(QueryConditionForm form) {
        return conditionallyQueryOfCheckedProject(form);
    }

    @Override
    public Result getHistoricalProjectInfo(HistoryQueryProjectInfo info) {

        List<ProjectGroup> list = projectGroupMapper.selectHistoricalInfoByUnitAndOperation(info.getOperationUnit(), info.getOperationType());
        for (ProjectGroup projectGroup : list
        ) {
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(projectGroup.getId(), null));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), projectGroup.getId(),JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public Result getAllOpenTopicByCondition(QueryConditionForm form) {
        //先查询出符合条件的ID，在进行条件查询
        List<Long> projectIdList = projectGroupMapper.conditionQuery(form);
        if (projectIdList.isEmpty()) {
            return Result.success(null);
        }
        //查询已选学生数量
        List<OpenTopicInfo> list = projectGroupMapper.getAllOpenTopic(projectIdList);
        for (OpenTopicInfo info : list
        ) {
            info.setAmountOfSelected(userProjectGroupMapper.getMemberAmountOfProject(info.getId(), null));
        }
        return Result.success(list);
    }

    private Result conditionallyQueryOfCheckedProject(QueryConditionForm form) {
        List<Long> projectIdList = projectGroupMapper.conditionQuery(form);
        if (projectIdList.isEmpty()) {
            return Result.success(null);
        }
        List<ProjectGroup> list = projectGroupMapper.selectAllByList(projectIdList);
        for (ProjectGroup projectGroup : list
        ) {
            Long id = projectGroup.getId();
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(id, null));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(null, id,JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public Result getToBeReportedProjectByLabLeader() {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        return getReportInfo(RoleType.LAB_ADMINISTRATOR.getValue());
    }

    @Override
    public Result getToBeReportedProjectBySecondaryUnit() {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        return getReportInfo(RoleType.SECONDARY_UNIT.getValue());
    }


    @Transactional(rollbackFor = GlobalException.class)
    public Result approveProjectApply(List<ProjectCheckForm> formList, Integer role) {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Integer operationUnit;
        //当前状态
        Integer projectStatus = null;
        //将要被更新成的状态
        Integer updateProjectStatus = null;
        switch (role) {
            //如果是实验室主任
            case 4:
                operationUnit = OperationUnit.LAB_ADMINISTRATOR.getValue();
                projectStatus = ProjectStatus.DECLARE.getValue();
                updateProjectStatus = ProjectStatus.LAB_ALLOWED.getValue();
                break;
            //如果是二级单位
            case 5:
                operationUnit = OperationUnit.SECONDARY_UNIT.getValue();
                projectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                updateProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                break;
            default:
                //超管执行操作
                operationUnit = -5;
        }
        List<OperationRecord> list = new LinkedList<>();
        for (ProjectCheckForm form : formList
        ) {
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationType(OperationType.AGREE.getValue());
            operationRecord.setOperationUnit(operationUnit);
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            list.add(operationRecord);
            //当角色是实验室主任的时候,项目状态不是
            ProjectGroup projectGroup = selectByProjectGroupId(form.getProjectId());
            if (role == 4 && !projectGroup.getStatus().equals(projectStatus)) {
                throw new GlobalException("项目编号为" + projectGroup.getId() + "的项目非申报状态", CodeMsg.PROJECT_STATUS_IS_NOT_DECLARE.getCode());
            }
            //如果不是实验室上报状态,抛出异常
            if (role.equals(RoleType.SECONDARY_UNIT.getValue())) {
                if (!projectGroup.getStatus().equals(projectStatus)) {
                    throw new GlobalException("项目编号为" + projectGroup.getId() + "的项目非实验室上报状态", CodeMsg.PROJECT_CURRENT_STATUS_ERROR.getCode());
                }
                //设置项目编号

                //获取最大的项目编号
                String serialNumber = projectGroupMapper.getMaxSerialNumberByCollege(user.getInstitute());
                //计算编号并在数据库中插入编号
                projectGroupMapper.updateProjectSerialNumber(form.getProjectId(), SerialNumberUtil.getSerialNumberOfProject(user.getInstitute(), ProjectType.GENERAL.getValue(), serialNumber));
            }
            //根据不同角色设置不同的项目状态
            updateProjectStatus(form.getProjectId(), updateProjectStatus);

        }
        recordMapper.multiInsert(list);
        return Result.success();
    }

    @Override
    public Result getAllOpenTopic() {
        List<OpenTopicInfo> list = projectGroupMapper.getAllOpenTopic(null);
        for (OpenTopicInfo info : list
        ) {
            info.setAmountOfSelected(userProjectGroupMapper.getMemberAmountOfProject(info.getId(), null));
        }
        return Result.success(list);
    }

    @Override
    public List getJoinInfo() {
        User currentUser = getUserService.getCurrentUser();
        //检测用户是不是老师--后期可省略
        if (currentUser == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }

        Role role = roleMapper.selectByUserId(Long.valueOf(currentUser.getCode()));
        if (role.getId() < (RoleType.MENTOR.getValue()).longValue()) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        List<JoinUnCheckVO> joinUnCheckVOS = new ArrayList<>();

        //获取当前教师参与申报的项目组
        List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), ProjectStatus.LAB_ALLOWED.getValue(), JoinStatus.JOINED.getValue());
        for (int i = 0; i < projectGroups.size(); i++) {
            if (projectGroups.get(i) == null) {
                i++;
            }
            if (projectGroups.get(i).getIsOpenTopic().equals(OpenTopicType.NOT_OPEN_TOPIC.getValue())) {
                if (i != projectGroups.size() - 1) {
                    i++;
                }
            }
            ProjectGroup projectGroup = projectGroups.get(i);
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(projectGroup.getId(), null);
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                joinUnCheckVOS.add(joinUnCheckVO);
            }
        }
        return joinUnCheckVOS;
    }

    @Override
    public Result getApplyingJoinInfoByCondition(MemberQueryCondition condition) {
        User currentUser = getUserService.getCurrentUser();
        //检测用户
        if (currentUser == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        if (condition == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        List<JoinUnCheckVO> joinUnCheckVOS = new ArrayList<>();
        if (condition.getId() == null) {
            List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), ProjectStatus.LAB_ALLOWED.getValue(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < projectGroups.size(); i++) {
                if (projectGroups.get(i) == null) {
                    break;
                }
                if (projectGroups.get(i).getIsOpenTopic().equals(OpenTopicType.NOT_OPEN_TOPIC.getValue())) {
                    i++;
                }
                ProjectGroup projectGroup = projectGroups.get(i);
                List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(projectGroup.getId(), condition.getStatus());
                for (UserProjectGroup userProjectGroup : userProjectGroups) {
                    JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                    joinUnCheckVOS.add(joinUnCheckVO);
                }
            }
            //编号，状态同时存在
        } else {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(condition.getId(), condition.getStatus());
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(condition.getId());
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                joinUnCheckVOS.add(joinUnCheckVO);
            }
        }
        return Result.success(joinUnCheckVOS);
    }

    @Override
    public Result addStudentToProject(JoinForm joinForm) {
        User user = getUserService.getCurrentUser();
        Long userId = Long.valueOf(user.getCode());
        UserProjectGroup userProjectGroupOfCurrentUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), userId);
        if (userProjectGroupOfCurrentUser == null || !userProjectGroupOfCurrentUser.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
            throw new GlobalException(CodeMsg.USER_NOT_IN_GROUP);
        }

        if (userMapper.selectByUserCode(String.valueOf(joinForm.getUserId())) == null) {
            throw new GlobalException(CodeMsg.USER_NO_EXIST);
        }

        if (userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), joinForm.getUserId()) != null) {
            throw new GlobalException(CodeMsg.USER_HAD_JOINED);
        }

        UserProjectGroup userProjectGroup = new UserProjectGroup();
        userProjectGroup.setUserId(joinForm.getUserId());
        userProjectGroup.setProjectGroupId(joinForm.getProjectGroupId());
        userProjectGroup.setMemberRole(MemberRole.NORMAL_MEMBER.getValue());
        userProjectGroup.setStatus(JoinStatus.JOINED.getValue());
        userProjectGroupMapper.insert(userProjectGroup);
        return Result.success();
    }

    @Override
    public Result removeStudentFromProject(JoinForm joinForm) {
        //验证项目状态
        Integer status = projectGroupMapper.selectByPrimaryKey(joinForm.getProjectGroupId()).getStatus();
        if (status > ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue()) {
            throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
        }

        User user = getUserService.getCurrentUser();
        Long userId = Long.valueOf(user.getCode());
        UserProjectGroup userProjectGroupOfCurrentUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), userId);
        if (userProjectGroupOfCurrentUser == null || !userProjectGroupOfCurrentUser.getMemberRole().equals(MemberRole.PROJECT_GROUP_LEADER.getValue())) {
            throw new GlobalException(CodeMsg.USER_NOT_IN_GROUP);
        }
        UserProjectGroup userProjectGroupOfJoinUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), joinForm.getUserId());
        if (userProjectGroupOfJoinUser == null) {
            throw new GlobalException(CodeMsg.USER_HAD_JOINED_CANT_REJECT);
        }
        userProjectGroupMapper.deleteByPrimaryKey(userProjectGroupOfCurrentUser.getId());
        return Result.success();
    }

    private JoinUnCheckVO getJoinUnCheckVO(UserProjectGroup userProjectGroup, ProjectGroup projectGroup) {
        User user = userService.selectByUserId(userProjectGroup.getUserId());
        JoinUnCheckVO joinUnCheckVO = new JoinUnCheckVO();
        joinUnCheckVO.setId(projectGroup.getId());
        joinUnCheckVO.setMemberRole(userProjectGroup.getMemberRole());
        joinUnCheckVO.setSerialNumber(projectGroup.getSerialNumber());
        joinUnCheckVO.setProjectName(projectGroup.getProjectName());
        joinUnCheckVO.setProjectType(projectGroup.getProjectType());
        joinUnCheckVO.setPersonJudge(userProjectGroup.getPersonalJudge());
        joinUnCheckVO.setTechnicalRole(userProjectGroup.getTechnicalRole());
        joinUnCheckVO.setApplyTime(userProjectGroup.getJoinTime());
        joinUnCheckVO.setExperimentType(projectGroup.getExperimentType());
        joinUnCheckVO.setUserDetailVO(convertUtil.convertUserDetailVO(user));
        joinUnCheckVO.setStatus(userProjectGroup.getStatus());

        return joinUnCheckVO;
    }

    @Override
    public Result rejectJoin(JoinForm[] joinForm) {
        for (JoinForm form : joinForm) {
            UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(form.getProjectGroupId(), form.getUserId());
            if (userProjectGroup == null) {
                return Result.error(CodeMsg.USER_NOT_APPLYING);
            }
            if (userProjectGroup.getStatus() != JoinStatus.APPLYING.getValue().intValue()) {
                return Result.error(CodeMsg.USER_HAD_JOINED_CANT_REJECT);
            }
            userProjectGroup.setStatus(JoinStatus.UN_PASS.getValue());
            if (!userProjectService.update(userProjectGroup)) {
                return Result.error(CodeMsg.UPDATE_ERROR);
            }
        }
        return Result.success();
    }

    @Override
    public List<SelectProjectVO> selectByProjectName(String name) {
        List<SelectProjectVO> list = (List<SelectProjectVO>) redisService.getList(ProjectGroupKey.getByFuzzyName, name);
        if (list == null || list.size() == 0) {
            list = projectGroupMapper.selectByFuzzyName(name);
            if (list != null) {
                redisService.setList(ProjectGroupKey.getByFuzzyName, name, list);
            }
        }
        return list;
    }

    @Override
    public Result rejectProjectApplyByLabAdministrator(List<ProjectCheckForm> formList) {
        //TODO 身份验证

        return rejectProjectApply(formList, OperationUnit.LAB_ADMINISTRATOR, OperationType.REJECT);
    }

    @Override
    public Result rejectProjectApplyBySecondaryUnit(List<ProjectCheckForm> formList) {
        //TODO 身份验证

        return rejectProjectApply(formList, OperationUnit.SECONDARY_UNIT, OperationType.REJECT);
    }

    @Override
    public Result rejectProjectApplyByFunctionalDepartment(List<ProjectCheckForm> formList) {
        //TODO 身份验证

        return rejectProjectApply(formList, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.REJECT);
    }

    @Override
    public Result rejectIntermediateInspectionProject(List<ProjectCheckForm> list) {
        return rejectProjectApply(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.CONCLUSION_REJECT);
    }

    @Override
    public Result rejectToBeConcludingProject(List<ProjectCheckForm> list) {
        return rejectProjectApply(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.CONCLUSION_REJECT);
    }

    /**
     * 因为是批量操作  所以就最好将拒绝和同意分开
     *
     * @param formList 项目拒绝信息集合
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    Result rejectProjectApply(List<ProjectCheckForm> formList, OperationUnit operationUnit, OperationType operationType) {
        User user = getUserService.getCurrentUser();
        List<OperationRecord> list = new LinkedList<>();
        Integer rightProjectStatus = null;
        switch (operationUnit.getValue()) {
            case 4:
                rightProjectStatus = ProjectStatus.DECLARE.getValue();
                break;
            case 5:
                rightProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                break;
            case 6:
                rightProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue();
                break;
            default:
                rightProjectStatus = ProjectStatus.ESTABLISH_FAILED.getValue();
                break;
        }
        for (ProjectCheckForm form : formList
        ) {
            Integer status = projectGroupMapper.selectByPrimaryKey(form.getProjectId()).getStatus();
            if (!rightProjectStatus.equals(status)) {
                throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
            }
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            //修改状态
            updateProjectStatus(form.getProjectId(), ProjectStatus.REJECT_MODIFY.getValue());
            list.add(operationRecord);
        }
        recordMapper.multiInsert(list);
        return Result.success();
    }

    private void setOperationExecutor(OperationRecord operationRecord) {
        User user = getUserService.getCurrentUser();
        Long id = Long.valueOf(user.getCode());
        operationRecord.setOperationExecutorId(id);
    }


    @Value(value = "${file.ip-address}")
    private String ipAddress;

    @Override
    public Result getProjectGroupDetailVOByProjectId(Long projectId) {
        if (projectId == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        ProjectGroupDetailVO detail = projectGroupMapper.getProjectGroupDetailVOByProjectId(projectId);
        //设置状态
        if (detail.getKeyProjectStatus() != null) {
            detail.setStatus(detail.getKeyProjectStatus());
        }
        ProjectFile file = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.APPLY_MATERIAL.getValue());
        if (file == null) {
            detail.setApplyurl(null);
        } else {
            String fileName = file.getFileName();
            String url = ipAddress + "/apply/" + fileName;
            detail.setApplyurl(url);
        }
        return Result.success(detail);
    }

    @Override
    public Result deleteMemberFromProject(Long projectId, Long userId) {
        User currentUser = getUserService.getCurrentUser();
        Long currentUserId = Long.valueOf(currentUser.getCode());
        if (userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, currentUserId) == null
                || !userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, currentUserId).getMemberRole()
                .equals(MemberRole.GUIDANCE_TEACHER.getValue()) ) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, userId);
        if (userProjectGroup == null) {
            throw new GlobalException(CodeMsg.USER_GROUP_NOT_EXIST);
        }
        userProjectGroupMapper.deleteByPrimaryKey(userProjectGroup.getId());
        return Result.success();
    }
}
