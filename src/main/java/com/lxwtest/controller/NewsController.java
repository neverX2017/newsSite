package com.lxwtest.controller;

import com.lxwtest.model.*;
import com.lxwtest.service.*;
import com.lxwtest.util.NewsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Controller
public class NewsController {
    private static final Logger logger = (Logger) LoggerFactory.getLogger(NewsController.class);

    @Autowired
    NewsService newsService;

    @Autowired
    QiniuService qiniuService;

    @Autowired
    HostHolder hostHolder;

    @Autowired
    UserService userService;

    @Autowired
    CommentService commentService;

    @Autowired
    LikeService likeService;


    @RequestMapping(path = {"/news/{newsId}"}, method = {RequestMethod.GET})
    public String newsDetail(@PathVariable("newsId") int newsId, Model model) {
        try {
            News news = newsService.getById(newsId);
            if (news != null) {
//                判断某条咨询喜欢以及不喜欢的数目
                int localUserId = hostHolder.getUser() != null ? hostHolder.getUser().getId() : 0;
                if (localUserId != 0) {
                    model.addAttribute("like", likeService.getLikeStatus(localUserId, EntityType.ENTITY_NEWS, news.getId()));
                } else {
                    model.addAttribute("like", 0);
                }
                //评论
                List<Comment> comments = commentService.getCommentsByEntity(news.getId(), EntityType.ENTITY_NEWS);
                List<ViewObject> commentVOs = new ArrayList<ViewObject>();
                for (Comment comment : comments) {
                    ViewObject commentVO = new ViewObject();
                    commentVO.set("comment", comment);
                    commentVO.set("user", userService.getUser(comment.getUserId()));
                    commentVOs.add(commentVO);
                }
                model.addAttribute("comments", commentVOs);
            }
            model.addAttribute("news", news);
            model.addAttribute("owner", userService.getUser(news.getUserId()));
        } catch (Exception e) {
            logger.error("获取资讯明细错误" + e.getMessage());
        }
        return "detail";
    }

//    增加评论
    @RequestMapping(path = {"/addComment"}, method = {RequestMethod.POST})
    public String addComment(@RequestParam("newsId") int newsId,
                             @RequestParam("content") String content) {
        try {
            Comment comment = new Comment();
            comment.setUserId(hostHolder.getUser().getId());
            comment.setContent(content);
            comment.setEntityType(EntityType.ENTITY_NEWS);
            comment.setEntityId(newsId);
            comment.setCreatedDate(new Date());
            comment.setStatus(0);
            commentService.addComment(comment);

            // 更新评论数量，以后用异步实现
            int count = commentService.getCommentCount(comment.getEntityId(), comment.getEntityType());
            newsService.updateCommentCount(comment.getEntityId(), count);

        } catch (Exception e) {
            logger.error("提交评论错误" + e.getMessage());
        }
        return "redirect:/news/" + String.valueOf(newsId);
    }

    //获取图片
    @RequestMapping(path="/image",method = {RequestMethod.GET})
    @ResponseBody
    public void getImage(@RequestParam("name")String imageName,
                         HttpServletResponse response){
        try {
            response.setContentType("image/jpeg");
            StreamUtils.copy(new FileInputStream(new File(NewsUtil.IMAGE_DIR + imageName)), response.getOutputStream());
        }catch (Exception e){
            logger.error("读取图片出错"+ e.getMessage());
        }

    }

    //某用户添加资讯
    @RequestMapping(path = {"/user/addNews"},method = {RequestMethod.POST})
    @ResponseBody
    public String addNews(@RequestParam("image") String image,
                          @RequestParam("title") String title,
                          @RequestParam("link") String link) {
        try {
            News news = new News();
            news.setImage(image);
            news.setCreatedDate(new Date());
            news.setTitle(title);
            news.setLink(link);
            if (hostHolder.getUser() != null) {
                news.setUserId(hostHolder.getUser().getId());
            } else {
                //设置一个匿名用户
                news.setUserId(3);
            }
            newsService.addNews(news);
            return NewsUtil.getJSONString(0);
        } catch (Exception e) {
            logger.error("添加资讯错误" + e.getMessage());
            return NewsUtil.getJSONString(1, "发布失败");
        }
    }

    @RequestMapping(path = {"/uploadImage/"}, method = {RequestMethod.POST}) //上传图片用POST
    @ResponseBody
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            //本地上传
//            String fileUrl = newsService.saveImage(file);
            //七牛云上传
            String fileUrl = qiniuService.saveImage(file);
            if (fileUrl == null) {
                return NewsUtil.getJSONString(1, "上传图片失败");
            }
            return NewsUtil.getJSONString(0, fileUrl);
        } catch (Exception e) {
            logger.error("上传图片失败" + e.getMessage());
            return NewsUtil.getJSONString(1, "上传失败");
        }
    }
}
