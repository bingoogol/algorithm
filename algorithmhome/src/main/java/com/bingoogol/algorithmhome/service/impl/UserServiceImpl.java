package com.bingoogol.algorithmhome.service.impl;

import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Resource;
import javax.mail.MessagingException;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bingoogol.algorithmhome.dao.AlgorithmUserDao;
import com.bingoogol.algorithmhome.dao.AttachmentDao;
import com.bingoogol.algorithmhome.dao.ModeratorInfoDao;
import com.bingoogol.algorithmhome.dao.UserDao;
import com.bingoogol.algorithmhome.dao.UserInfoDao;
import com.bingoogol.algorithmhome.dto.ApplyDto;
import com.bingoogol.algorithmhome.dto.UserLoginDto;
import com.bingoogol.algorithmhome.dto.UserRegistDto;
import com.bingoogol.algorithmhome.exception.IllegalClientException;
import com.bingoogol.algorithmhome.service.UserService;
import com.bingoogol.algorithmhome.util.MailUtil;
import com.bingoogol.algorithmhome.util.SecurityUtil;

@Service
@Transactional
public class UserServiceImpl implements UserService {
	@Resource
	private UserDao userDao;
	@Resource
	private UserInfoDao userInfoDao;
	@Resource
	private AttachmentDao attachmentDao;
	@Resource
	private ModeratorInfoDao moderatorInfoDao;
	@Resource
	private AlgorithmUserDao algorithmUserDao;

	@Override
	public String register(UserRegistDto userRegistDto) {
		if (isEmailAvailable(userRegistDto.getUsername()) && isEmailAvailable(userRegistDto.getEmail())) {
			String activecode = UUID.randomUUID().toString();
			Timestamp expiretime = new Timestamp(System.currentTimeMillis() + 1000 * 3600 * 24);
			userRegistDto.setId(UUID.randomUUID().toString());
			try {
				userRegistDto.setPassword(SecurityUtil.md5(userRegistDto.getUsername(), userRegistDto.getPassword()));
				if (userDao.register(userRegistDto) == 1) {
					if (userDao.addUserInfo(userRegistDto.getId(), activecode, expiretime, userRegistDto.getCid()) == 1) {
						MailUtil.sendActiveLink(userRegistDto.getEmail(), userRegistDto.getUsername(), userRegistDto.getId(), activecode);
						return userRegistDto.getId();
					}
				}
				return null;
			} catch (NoSuchAlgorithmException e) {
				// 加密错误
				throw new IllegalClientException("系统内部错误");
			} catch (MessagingException e) {
				throw new IllegalClientException("邮件发送失败");
			}
		} else {
			throw new IllegalClientException("没有通过浏览器注册");
		}
	}

	@Override
	public boolean isUsernameAvailable(String username) {
		return userDao.getCountByUsername(username) == 0 ? true : false;
	}

	@Override
	public boolean isEmailAvailable(String email) {
		return userDao.getCountByEmail(email) == 0 ? true : false;
	}

	@Override
	public boolean active(String id, String activecode) {
		try {
			Map<String, Object> user = userDao.findUserInfoById(id);
			if (user.get("activecode").equals(activecode) && new Timestamp(System.currentTimeMillis()).before((Timestamp) user.get("expiretime"))) {
				if (userDao.changeStatus(id, 4) == 1) {
					return true;
				}
			}
			return false;
		} catch (EmptyResultDataAccessException e) {
			return false;
		}
	}

	@Override
	public Map<String, Object> login(UserLoginDto userLoginDto) {
		try {
			userLoginDto.setPassword(SecurityUtil.md5(userLoginDto.getUsername(), userLoginDto.getPassword()));
			return userDao.login(userLoginDto);
		} catch (EmptyResultDataAccessException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	@Override
	public boolean resendemail(String id) {
		try {
			Map<String, Object> map = userDao.resendemail(id);
			String activecode = UUID.randomUUID().toString();
			Timestamp expiretime = new Timestamp(System.currentTimeMillis() + 1000 * 3600 * 24);
			if (userDao.updateActiveUserInfo(id, activecode, expiretime) == 1) {
				MailUtil.sendActiveLink((String) map.get("email"), (String) map.get("username"), id, activecode);
				return true;
			}
		} catch (EmptyResultDataAccessException e) {
			throw new IllegalClientException("没有通过浏览器注册");
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public int getGold(String id) {
		try {
			return userDao.getGold(id);
		} catch (EmptyResultDataAccessException e) {
			throw new IllegalClientException("没有通过浏览器注册");
		}
	}

	@Override
	public boolean buy(String aid, String buyerid, String sellerid, int price) {
		int i = userInfoDao.plusPrice(sellerid, price);
		int j = userInfoDao.minusPrice(buyerid, price);
		int k = algorithmUserDao.add(buyerid, aid);
		if (i + k + j  == 3) {
			return true;
		}
		return false;
	}

	@Override
	public boolean apply(ApplyDto applyDto) {
		applyDto.setApprove(UUID.randomUUID().toString());
		int i = attachmentDao.addAttachment(applyDto.getApprove(), applyDto.getApproveName(), applyDto.getApproveHash());
		int j = userDao.setUpdateInfo(applyDto.getUid());
		int k = moderatorInfoDao.add(applyDto);
		if (i + k + j == 3) {
			return true;
		}
		return false;
	}
}
