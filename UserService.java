package com.example.molisheservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.molisheservice.DTO.CreateShopDTO;
import com.example.molisheservice.DTO.ReviewDTO;
import com.example.molisheservice.DTO.UserDTO;
import com.example.molisheservice.DTO.UserItemShopDTO;
import com.example.molisheservice.bean.FavoriteResponseDataBean;
import com.example.molisheservice.bean.Result;
import com.example.molisheservice.config.MyException.ShopExistsException;
import com.example.molisheservice.entity.*;
import com.example.molisheservice.mapper.*;
import com.example.molisheservice.util.ActiveStatus;
import com.example.molisheservice.util.FileUtil;
import com.example.molisheservice.util.InsertStatus;
import com.example.molisheservice.util.NotNullAndNotEmptyCondition;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UserService
 *
 */
@Service
@RequiredArgsConstructor
public class UserService {

    @Autowired
    private UserMapper mapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private ShopUserReviewMapper shopUserReviewMapper;

    @Autowired
    private ShopUserItemMapper shopUserItemMapper;

    @Autowired
    private ItemUserMapper itemUserMapper;

    @Autowired
    private ShopUserMapper shopUserMapper;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private FileUtil fileUtil;

    @Autowired
    private ShopService shopService;

    /**
     *
     *
     * @return
     */
    public List<User> getAllUser(){ return mapper.selectList(null); }

    public List<User> getAllUser(String word){
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>();
        queryWrapper.like(User::getUsername, word);
        return mapper.selectList(queryWrapper);
    }

    public Result deleteUser() {
        User user = mapper.selectById(fetchUserIdFromSecurityContentHolder());
        if (user != null && !user.isDeleted()) {
            user.setUserdeleted(ActiveStatus.Deleted.toString());
            mapper.updateById(user);
            return Result.success(null, "User " + user.getUsername() + " is deleted.");
        }
        return Result.error(null, "User is null or deleted.");
    }

    public List<ReviewDTO> getAllCurrentUserShopComment() {
        List<ShopUserReview> shopUserReviews = findShopUserReviewsByUserId(fetchUserIdFromSecurityContentHolder());
        if (shopUserReviews == null || shopUserReviews.isEmpty())
            return Collections.emptyList();
        List<Long> reviewIdList = shopUserReviews.stream()
                .map(ShopUserReview::getReviewId)
                .collect(Collectors.toList());
        Map<Long, Review> reviewMap = reviewMapper.selectBatchIds(reviewIdList)
                .stream()
                .filter(review -> review != null && !review.isDeleted())
                .collect(Collectors.toMap(Review::getId, review -> review));
        return shopUserReviews.stream()
                .filter(sur -> reviewMap.containsKey(sur.getReviewId()))
                .map(sur -> new ReviewDTO(reviewMap.get(sur.getReviewId()), sur.getShopId()))
                .collect(Collectors.toList());
    }

    public Result likeOrDislikeShop(ShopUser shopUser) {
        Long userId = fetchUserIdFromSecurityContentHolder();
        QueryWrapper<ShopUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                .eq("shopId", shopUser.getShopId());
        ShopUser existingRecord = shopUserMapper.selectOne(queryWrapper);
        if (existingRecord != null) {
            int deleteResult = shopUserMapper.delete(queryWrapper);
            if (deleteResult > 0)
                return Result.success("unliked Successfully");
            return Result.error();
        }
        ShopUser newUserShop = new ShopUser();
        newUserShop.setUserId(userId);
        newUserShop.setShopId(shopUser.getShopId());
        int insertResult = shopUserMapper.insert(newUserShop);
        if (insertResult > 0)
            return Result.success(new FavoriteResponseDataBean("Shop Liked"));
        return Result.error();
    }

    public List<Shop> getFavoriteShop(){
        QueryWrapper<ShopUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", fetchUserIdFromSecurityContentHolder());
        List<ShopUser> shopUsers = shopUserMapper.selectList(queryWrapper);
        if (shopUsers.isEmpty())
            return Collections.emptyList();
        List<Long> shopIds = shopUsers.stream().map(ShopUser::getShopId).collect(Collectors.toList());
        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        shopService.setPhotoToShop(shops);
        return shops;
    }

    public List<Item> getFavoriteItem(){
        QueryWrapper<ShopUserItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", fetchUserIdFromSecurityContentHolder());
        List<ShopUserItem> itemUsers = shopUserItemMapper.selectList(queryWrapper);
        if (itemUsers.isEmpty())
            return Collections.emptyList();
        List<Long> itemIds = itemUsers.stream().map(ShopUserItem::getItemId).collect(Collectors.toList());
        return itemMapper.selectBatchIds(itemIds);
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<User> queryWrapper = new QueryWrapper<User>()
                .lambda()
                .eq(User::getUsername, username);
        User user = mapper.selectOne(queryWrapper);
        if (Objects.isNull(user))
            throw new RuntimeException("Username or password invalid");
        return new LoginUser(user);
    }

    private boolean userExisted(UserDTO userDTO) {
        // hidden
    }

    private List<ShopUserReview> findShopUserReviewsByUserId(Long userId) {
        // hidden
    }

    private Long fetchUserIdFromSecurityContentHolder() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return loginUser.getUser().getId();
    }
}
