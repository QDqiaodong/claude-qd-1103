package com.milktea.bar.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.milktea.bar.common.BizException;
import com.milktea.bar.common.Dict;
import com.milktea.bar.entity.Member;
import com.milktea.bar.repository.MemberRepository;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> list(String level) {
        if (level != null) {
            return memberRepository.findAll().stream()
                .filter(m -> level.equals(m.level)).toList();
        }
        return memberRepository.findAll();
    }

    public Member create(Member input) {
        if (input.phone == null || input.phone.isBlank()) {
            throw new BizException("手机号不能为空");
        }
        if (memberRepository.existsByPhone(input.phone)) {
            throw new BizException("手机号已存在：" + input.phone);
        }
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("昵称不能为空");
        }
        if (input.points == null) {
            input.points = 0;
        }
        if (input.points < 0) {
            throw new BizException("积分不能为负");
        }
        Member saved = new Member();
        saved.phone = input.phone;
        saved.name = input.name;
        saved.points = input.points;
        saved.level = (input.level == null) ? "普通" : input.level;
        if (!Dict.isValidMemberLevel(saved.level)) {
            throw new BizException("会员等级必须是：普通 / 银卡 / 金卡");
        }
        saved.createdAt = LocalDateTime.now();
        return memberRepository.save(saved);
    }

    public Member update(Long id, Member input) {
        Member existing = memberRepository.findById(id)
            .orElseThrow(() -> new BizException("会员不存在：" + id));
        if (input.phone != null && !input.phone.equals(existing.phone)) {
            if (memberRepository.existsByPhoneAndIdNot(input.phone, id)) {
                throw new BizException("手机号已存在：" + input.phone);
            }
            existing.phone = input.phone;
        }
        if (input.name != null) {
            existing.name = input.name;
        }
        if (input.points != null) {
            if (input.points < 0) {
                throw new BizException("积分不能为负");
            }
            existing.points = input.points;
            existing.level = Dict.decideMemberLevel(existing.points);
        }
        if (input.level != null) {
            if (!Dict.isValidMemberLevel(input.level)) {
                throw new BizException("会员等级必须是：普通 / 银卡 / 金卡");
            }
            existing.level = input.level;
        }
        return memberRepository.save(existing);
    }

    /**
     * 调整积分（消费累加 / 退款回退），并按累计积分重算等级。
     */
    public Member addPoints(Long id, int delta) {
        Member existing = memberRepository.findById(id)
            .orElseThrow(() -> new BizException("会员不存在：" + id));
        int newPoints = existing.points + delta;
        if (newPoints < 0) {
            throw new BizException("积分回退后不能为负（当前 " + existing.points + "，回退 " + delta + "）");
        }
        existing.points = newPoints;
        existing.level = Dict.decideMemberLevel(newPoints);
        return memberRepository.save(existing);
    }
}
