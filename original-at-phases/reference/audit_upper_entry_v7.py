#!/usr/bin/env python3
"""HYPER_DOG v7: upper-origin comeback restores upper AT and counts 32 paid rounds.
Game inputs, odds and multipliers load from the versioned v7 JSON.
Stages NORMAL, LOWER_INITIAL, LOWER_AT, UPPER_INITIAL, UPPER_AT, UPPER_CZ.
"""
import json, math, argparse, pathlib, time
import numpy as np
from numba import njit

ROOT=pathlib.Path(__file__).resolve().parent
DATA=ROOT/'pirijuggler_new_machine_calibrated_v7.json'
OUT=ROOT/'piri_full_sim_result_v7.json'
ROLES=['MISS','REPLAY','GRAPE','WEAK_CHERRY','WATERMELON','CHANCE','STRONG_CHERRY','STRONG_CHANCE','BAR']
MISS,REPLAY,GRAPE,WEAK,WM,CHANCE,STRONG,SC,BAR=range(9)
MODES=['LOW_A','LOW_B','NORMAL_A','NORMAL_B','HIGH','SUPER_HIGH']
NORMAL, LOWER_INITIAL, LOWER_AT, UPPER_INITIAL, UPPER_AT, UPPER_CZ = range(6)

def weighted_cdf(mp):
    out=np.zeros(len(MODES),dtype=np.float64)
    acc=0.0
    for key, weight in mp.items():
        acc+=weight/100
        out[MODES.index(key)]=acc
    if abs(acc-1)>1e-9:raise ValueError(f'bad sum {mp} {acc}')
    # Empty values remain 0, a natural CDF with np.searchsorted would break for zeros in middle; iterate until u< cdf with positive weights
    return np.array([mp.get(name,0)/100 for name in MODES],dtype=np.float64)

def make_params(d):
    common=d['normal_and_at_common_role_draw']; normal=d['normal_mode']; lower=d['lower_at']; upper=d['upper_at']
    probs=np.zeros((3,len(ROLES)), dtype=np.float64)
    for k,s in enumerate(('NORMAL','LOWER_AT','UPPER_AT')):
        for i,name in enumerate(ROLES):
            if name=='MISS': continue
            if name=='GRAPE':probs[k,i]=common['grape_probability_per_game'][s]
            else:probs[k,i]=common['exclusive_base_role_probability_per_game'][name]
        probs[k,0]=1-sum(probs[k])
    assert np.allclose(probs.sum(axis=1),1) and np.all(probs>=0)
    cum=np.cumsum(probs,axis=1)
    by_mode=np.zeros((4,6,6),dtype=np.float64)
    for tier_idx,tier in enumerate(('STANDARD','STRONG','FAVORED','VERY_STRONG')):
        for i,mode in enumerate(MODES):
            by_mode[tier_idx,i]=weighted_cdf(normal['mode_up_destination_weights_percent_by_tier'][tier][mode])
    drop=np.array([normal['miss_mode_drop'][mode]['probability_on_miss'] for mode in MODES],dtype=np.float64)
    drop_dest=np.zeros((6,6),dtype=np.float64)
    for i,mode in enumerate(MODES):
        if drop[i]>0:drop_dest[i]=weighted_cdf(normal['miss_mode_drop'][mode]['destinations'])
    exits=np.stack([weighted_cdf(normal['at_exit_mode_weights_percent_by_saved_at_entry_mode'][mode]) for mode in MODES]);start=weighted_cdf(normal['startup_initial_mode_weights_percent'])
    base_at=np.array([1/normal['base_single_at_hit_denominator_by_mode'][m] for m in MODES],dtype=np.float64)
    settings=np.array([normal['single_hit_probability_multiplier_by_setting'][str(i)] for i in range(1,7)],dtype=np.float64)
    # Weighted medal distributions sampled from lower spec, encoded 16 fixed rows, cdf-like selection inside numba
    # Inputs for lower initial: numeric array of support amounts and weights.
    init=lower['initial']; init_table=np.zeros((9,2,8),dtype=np.float64); init_len=np.zeros(9,dtype=np.int64)
    for role,dict_ in init['role_bonus_medals_weights_percent'].items():
        i=ROLES.index(role);init_len[i]=len(dict_)
        for j,(amt,w) in enumerate(dict_.items()):init_table[i,0,j]=int(amt);init_table[i,1,j]=w/100
    ind=np.array([[int(k),v/100] for k,v in init['independent_extra_medals_weights_percent'].items()],dtype=np.float64)
    # table 0 weak,1 watermelon,2 consecutiveWeak,3 consecutiveWatermelon,4 sc ordinary,5 sc cz,6 chance ordinary,7 chance cz,8 strong chance
    maps=[lower['rare_role_addon_weights_percent'][name] for name in ('WEAK_CHERRY','WATERMELON','WEAK_CHERRY_SECOND_CONSECUTIVE_EXTRA','WATERMELON_SECOND_CONSECUTIVE_EXTRA')]
    maps+= [lower['rare_role_addon_weights_percent']['STRONG_CHERRY'][t]['medal_weights'] for t in ('ordinary','cz_award')]
    maps+= [lower['rare_role_addon_weights_percent']['CHANCE'][t]['medal_weights'] for t in ('ordinary','cz_award')]
    maps+= [lower['rare_role_addon_weights_percent']['STRONG_CHANCE']]
    amt=np.zeros((len(maps),8),dtype=np.int64); pct=np.zeros((len(maps),8),dtype=np.float64); n=np.zeros(len(maps),dtype=np.int64)
    for i,mp in enumerate(maps):
        n[i]=len(mp)
        for j,(a,p) in enumerate(mp.items()):amt[i,j]=int(a);pct[i,j]=p/100
        assert abs(sum(pct[i])-1)<1e-8
    force_dist=np.zeros(9,dtype=np.float64)
    for k,v in upper['initial']['forced_rare_slot_distribution_percent'].items():force_dist[ROLES.index(k)]=v/100
    prem=np.zeros(9,dtype=np.float64)
    prem[STRONG]=upper['regular_addons']['strong_cherry_2000_override_percent']/100
    prem[SC]=upper['regular_addons']['strong_chance_2000_override_percent']/100
    pay=np.zeros((3,9),dtype=np.int64)
    for i,s in enumerate(('NORMAL','LOWER_AT','UPPER_AT')):
        for j,role in enumerate(ROLES):pay[i,j]=d['fixed_requirements']['payout_regular_or_at'][role][s]
    # For 3,4,5 streak rules and mode ups: table, explicit indexes: rp3,rp4,rp5,gp3,gp4,gp5, wk1,wk2, wm1,wm2, strong,chance,sc
    keys=['REPLAY_STREAK_3','REPLAY_STREAK_4','REPLAY_STREAK_5','GRAPE_STREAK_3','GRAPE_STREAK_4','GRAPE_STREAK_5','WEAK_CHERRY_SINGLE','WEAK_CHERRY_2PLUS_CONSECUTIVE','WATERMELON_SINGLE','WATERMELON_2PLUS_CONSECUTIVE','STRONG_CHERRY','CHANCE','STRONG_CHANCE']
    linked=np.array([normal['role_linked_at_hit_percent'].get(k,0)/100 for k in keys])
    modeup=np.array([normal['mode_up_roll_percent_by_event'].get(k,0)/100 for k in keys])
    tier_to_i={'STANDARD':0,'STRONG':1,'FAVORED':2,'VERY_STRONG':3}
    tieridx=np.array([tier_to_i.get(normal['mode_up_table_selector'].get(k,''),-1) for k in keys],dtype=np.int64)
    fcd=float(d['upper_cz']['independent_p_success_each_game']); ncd=float(d['after_at_comeback']['independent_extra_at_hit_probability_each_game'])
    return (cum,by_mode,drop,drop_dest,exits,start,base_at,settings,init_table,init_len,ind,amt,pct,n,force_dist,prem,pay,linked,modeup,tieridx,fcd,ncd,
            float(common['upper_miss_to_rare_role_rewrite']['conditional_probability_on_miss']),
            float(lower['rare_role_addon_weights_percent']['STRONG_CHERRY']['cz_award']['probability_percent']/100),
            float(lower['rare_role_addon_weights_percent']['CHANCE']['cz_award']['probability_percent']/100),
            int(d['bar_special']['normal_direct_upper']['dedicated_bonus_medals']),
            np.array([upper['regular_addons']['role_multiplier_from_lower_by_role'].get(role,0) for role in ROLES],dtype=np.int64),
            float(normal['rare_role_linked_at_hit_scale']))

@njit

def choice(weights):
    v=np.random.random();summ=0.
    for i in range(len(weights)):
        summ+=weights[i]
        if v < summ:return i
    return len(weights)-1

@njit

def draw_role(cum,profile,exclude_bar=False):
    u=np.random.random()
    # easiest: if u in BAR mass (end, +1/65536), convert to miss
    for i in range(9):
        if u<cum[profile,i]:
            if i==BAR and exclude_bar:return MISS
            return i
    return MISS

@njit

def choose_medal(amount,weight,count):
    u=np.random.random();q=0.
    for k in range(count):
        q+=weight[k]
        if u<q:return amount[k]
    return amount[count-1]

@njit

def weighted_pick_row(matrix,row):
    return choice(matrix[row])

@njit

def role_rare_rank(role):
    if role==WEAK:return 1
    if role==WM:return 2
    if role==CHANCE:return 3
    if role==STRONG:return 4
    if role==SC:return 5
    return 0

@njit

def lower_bonus(role,streak,amt,pct,n,strong_branch,chance_branch):
    ticket=0
    if role==WEAK or role==WM:
        idx=0 if role==WEAK else 1
        amount=choose_medal(amt[idx],pct[idx],n[idx])
        if streak>=2:
            x=idx+2
            amount+=choose_medal(amt[x],pct[x],n[x])
        return amount,ticket
    if role==STRONG:
        ticket=1 if np.random.random()<strong_branch else 0
        idx=5 if ticket else 4
        return choose_medal(amt[idx],pct[idx],n[idx]),ticket
    if role==CHANCE:
        ticket=1 if np.random.random()<chance_branch else 0
        idx=7 if ticket else 6
        return choose_medal(amt[idx],pct[idx],n[idx]),ticket
    if role==SC:
        return choose_medal(amt[8],pct[8],n[8]),1
    return 0,0

@njit

def upper_bonus(role,streak,amt,pct,n,prem,strong_branch,chance_branch,role_scales):
    if (role==STRONG or role==SC) and np.random.random()<prem[role]:return 2000,True
    b,t=lower_bonus(role,streak,amt,pct,n,strong_branch,chance_branch)
    return min(2000,b*role_scales[role]),False

@njit

def simulate(setting, target_paid, seed, p, record_at_max=300000, record_upper_max=500000):
    (cum,up,drop,drop_dest,exits,start,base_at,settings,init_table,init_len,ind,amt,pct,n,force_dist,prem,pay,linked,modeup,tieridx,pcz,pback,pwrite,pstrong,pchance,bar_extra,role_scales,linked_rare_scale)=p
    np.random.seed(seed)
    # 3 buckets: normal lower upper plus special stage counter
    inn=np.zeros(3,dtype=np.int64); out=np.zeros(3,dtype=np.int64);games=np.zeros(3,dtype=np.int64)
    special=np.zeros(3,dtype=np.int64) # lower init, upper init, cz
    initial_count=np.zeros(2,dtype=np.int64); upper_entries=0;lower_entries=0
    cz_counts=np.zeros(6,dtype=np.int64) # 0 lower cases, 1 lower succ, 2 upper cases, 3 upper succ, 4 lower guaranteed, 5 upper guaranteed
    bar_counts=np.zeros(3,dtype=np.int64)
    two_thousand=0; upper_rarewrites=0
    max_at=0;sum_at=0;sum_at_paid=0
    completed_at=0
    at_net_samples=np.zeros(record_at_max,dtype=np.int64)
    single_hits=0; linked_hits=0; extra_hits=0
    at_starts=0; normal_paid_since_start=0; at_total_paid=0
    total_special_payout=0; total_special_bet=0
    total_awarded_bonus=0;mean_lower_initial=0;mean_upper_initial=0
    mode=choice(start);saved_hit_mode=0
    stage=NORMAL; balance=0
    streak_role=-1; streak_n=0; free_replay=False
    tickets=0;bar_guaranteed=False;bar_forced=0;pending_lower_force=0
    upcoming_cz_guaranteed=False;cz_from_upper=False;cz_game=0;cz_force_game=0
    upper_init_index=0;upper_initial_payout=0;lower_initial_payout=0
    upper_force_n=0;upper_force_start=0
    special_streak_n=0;special_streak_role=-1
    comeback_left=0; comeback_from_upper=False
    comeback_upper_returns=0; comeback_lower_returns=0
    comeback_attempts_upper=0; comeback_attempts_lower=0
    normal_non_comeback_games=0;normal_non_comeback_hits=0
    comeback_free_games=0;comeback_paid_games=0
    at_session_net=0; at_session_paid=0
    # One upper episode starts with the FIRST upper initial and ends on a failed upper CZ.
    # A successful upper CZ is a continuation, not a new entrance.
    # A comeback after a failed upper CZ is a NEW upper episode (normal play intervenes).
    upper_episodes=0;upper_completed=0;upper_gain=0;upper_gain_sum=0
    upper_source=0
    upper_source_count=np.zeros(4,dtype=np.int64)
    upper_source_gain=np.zeros(4,dtype=np.int64)
    upper_source_names_count=np.zeros(4,dtype=np.int64)
    upper_reloops=0
    upper_samples=np.zeros(record_upper_max,dtype=np.int64)
    upper_entry_net_games=0

    # run until count reaches, but if in AT finish that chain to avoid censored records
    watchdog=0
    while (games[0]+games[1]+games[2]<target_paid or stage!=NORMAL):
        watchdog+=1
        if watchdog>target_paid*15+200000:raise RuntimeError('Too many steps, possible AT absorbing loop')
        if stage==NORMAL:
            role=draw_role(cum,0)
            bet=0 if free_replay else 3
            free_replay = (role==REPLAY)
            payout=pay[0,role]
            if comeback_left>0:
                if bet==0:comeback_free_games+=1
                else:comeback_paid_games+=1
            else:normal_non_comeback_games+=1
            inn[0]+=bet;out[0]+=payout;games[0]+=1
            at_session_net=0
            if role==streak_role:streak_n+=1
            else:streak_role=role;streak_n=1
            event=-1
            if role==REPLAY and streak_n>=3:event=min(streak_n,5)-3
            elif role==GRAPE and streak_n>=3:event=3+min(streak_n,5)-3
            elif role==WEAK:event=7 if streak_n>=2 else 6
            elif role==WM:event=9 if streak_n>=2 else 8
            elif role==STRONG:event=10
            elif role==CHANCE:event=11
            elif role==SC:event=12
            was_mode=mode
            single = np.random.random()< min(.95,base_at[mode]*settings[setting-1])
            linked_hit=event>=0 and np.random.random()<linked[event]*(linked_rare_scale if event>=6 else 1.0)
            # mode shift even if AT; BAR overrides
            if event>=0 and tieridx[event]>=0 and np.random.random()<modeup[event]:mode=weighted_pick_row(up[tieridx[event]],mode)
            elif role==MISS and np.random.random()<drop[mode]:mode=weighted_pick_row(drop_dest,mode)
            if role==BAR or single or linked_hit or (comeback_left>0 and np.random.random()<pback):
                if role==BAR:bar_counts[0]+=1
                elif single:single_hits+=1
                elif linked_hit:linked_hits+=1
                else:extra_hits+=1
                saved_hit_mode=mode
                if comeback_left==0:normal_non_comeback_hits+=1
                at_starts+=1;at_session_net=0;at_session_paid=0
                if comeback_left>0:
                    if comeback_from_upper:comeback_upper_returns+=1
                    else:comeback_lower_returns+=1
                upper_return=(comeback_left>0 and comeback_from_upper)
                streak_role=-1;streak_n=0;free_replay=False
                comeback_left=0;comeback_from_upper=False
                tickets=0; bar_guaranteed=False;bar_forced=0;pending_lower_force=0
                if role==BAR or upper_return:
                    balance=bar_extra if role==BAR else 0
                    upper_source=3 if upper_return else 0
                    upper_source_count[upper_source]+=1
                    upper_episodes+=1;upper_gain=0
                    stage=UPPER_INITIAL;upper_entries+=1
                    upper_init_index=0;upper_initial_payout=0;upper_force_n=0;special_streak_n=0;special_streak_role=-1
                else:
                    stage=LOWER_INITIAL;lower_entries+=1
            else:
                if comeback_left>0 and bet==3:
                    comeback_left-=1
                    if comeback_left==0:comeback_from_upper=False
        elif stage==LOWER_INITIAL:
            special[0]+=1;initial_count[0]+=1
            role=draw_role(cum,0,True)
            bonus=int(init_table[role,0,0])
            if init_len[role]>0:
                bonus=choose_medal(init_table[role,0],init_table[role,1],init_len[role])
            ex=choose_medal(ind[:,0],ind[:,1],len(ind))
            balance=150+bonus+ex
            mean_lower_initial+=balance
            free_replay=False;streak_role=-1;streak_n=0
            stage=LOWER_AT
        elif stage==LOWER_AT or stage==UPPER_AT:
            k=1 if stage==LOWER_AT else 2
            role=draw_role(cum,k)
            if stage==UPPER_AT and role==MISS and np.random.random()<pwrite:
                r=choice(np.array([.5,.25,.15,.07,.03]))
                role=np.array([WEAK,WM,CHANCE,STRONG,SC])[r]
                upper_rarewrites+=1
            bet=0 if free_replay else 3
            free_replay=(role==REPLAY)
            payout=pay[k,role]
            inn[k]+=bet;out[k]+=payout;games[k]+=1
            at_session_net+=payout-bet
            at_session_paid+=1
            if k==2:
                upper_gain+=payout-bet
                upper_entry_net_games+=1
            balance-=payout-bet
            if role==streak_role:streak_n+=1
            else:streak_role=role;streak_n=1
            if role==BAR:
                bar_counts[k]+=1
                if k==1:
                    # lower BAR: carry tickets into future upper CZ; keep all rights
                    if tickets>=1:
                        bar_guaranteed=True
                        pending_lower_force+=max(0,tickets-1)
                        tickets=0
                    balance+=bar_extra
                    upper_source=1
                    upper_source_count[upper_source]+=1
                    upper_episodes+=1;upper_gain=0
                    stage=UPPER_INITIAL;upper_entries+=1
                    upper_init_index=0;upper_initial_payout=0;upper_force_n=0
                    free_replay=False;streak_n=0;streak_role=-1;special_streak_n=0;special_streak_role=-1
                    continue
                else:
                    bar_guaranteed=True;bar_forced+=1
            if role!=BAR:
                if stage==LOWER_AT:
                    bonus,t=lower_bonus(role,streak_n,amt,pct,n,pstrong,pchance)
                    tickets+=t
                else:
                    bonus,is2000=upper_bonus(role,streak_n,amt,pct,n,prem,pstrong,pchance,role_scales)
                    if is2000:two_thousand+=1
                balance+=bonus
                total_awarded_bonus+=bonus
            if balance<=0:
                free_replay=False;streak_n=0;streak_role=-1
                stage=UPPER_CZ if (k==2 or tickets>0) else NORMAL
                if stage==UPPER_CZ:
                    cz_from_upper=(k==2)
                    guaranteed= (k==2 and bar_guaranteed) or (k==1 and tickets>=2)
                    if k==1 and tickets>=2:cz_counts[4]+=1
                    if k==2 and bar_guaranteed:cz_counts[5]+=1
                    upcoming_cz_guaranteed=guaranteed
                    cz_force_game=np.random.randint(1,6) if guaranteed else 0
                    cz_game=0
                    if k==1:cz_counts[0]+=1
                    else:cz_counts[2]+=1
                    special_streak_n=0;special_streak_role=-1
                else:
                    # No ticket on lower -- end AT and launch comeback
                    completed_at+=1;sum_at+=at_session_net;sum_at_paid+=at_session_paid
                    if completed_at<=record_at_max:at_net_samples[completed_at-1]=at_session_net
                    if at_session_net>max_at:max_at=at_session_net
                    mode=weighted_pick_row(exits,saved_hit_mode)
                    comeback_left=32;comeback_from_upper=False;comeback_attempts_lower+=1
        elif stage==UPPER_CZ:
            special[2]+=1
            # draw a flag for visual reel outcomes, never use it for success
            role=draw_role(cum,0,True)
            cz_game+=1
            success=(np.random.random()<pcz) or (upcoming_cz_guaranteed and cz_game>=cz_force_game)
            if success:
                cz_counts[3 if cz_from_upper else 1]+=1
                # carry upper BAR bonuses into next initial, including forced rare
                if cz_from_upper:
                    upper_force_n=bar_forced + pending_lower_force
                    pending_lower_force=0
                else:
                    upper_force_n=max(0,tickets-2)
                tickets=0;bar_forced=0;bar_guaranteed=False
                if cz_from_upper:
                    upper_reloops+=1
                else:
                    upper_source=2
                    upper_source_count[upper_source]+=1
                    upper_episodes+=1;upper_gain=0
                stage=UPPER_INITIAL;upper_entries+=1
                upper_init_index=0;upper_initial_payout=0;special_streak_n=0;special_streak_role=-1
            elif cz_game>=5:
                tickets=0;bar_guaranteed=False;bar_forced=0;pending_lower_force=0
                free_replay=False;streak_n=0;streak_role=-1
                if cz_from_upper:
                    upper_gain_sum+=upper_gain
                    upper_source_gain[upper_source]+=upper_gain
                    if upper_completed<record_upper_max:upper_samples[upper_completed]=upper_gain
                    upper_completed+=1
                    stage=NORMAL;completed_at+=1;sum_at+=at_session_net;sum_at_paid+=at_session_paid
                    if completed_at<=record_at_max:at_net_samples[completed_at-1]=at_session_net
                    if at_session_net>max_at:max_at=at_session_net
                    mode=weighted_pick_row(exits,saved_hit_mode)
                    comeback_left=32;comeback_from_upper=True;comeback_attempts_upper+=1
                else:
                    stage=LOWER_INITIAL;lower_entries+=1
        elif stage==UPPER_INITIAL:
            special[1]+=1
            role=draw_role(cum,2,True)
            if upper_init_index<min(upper_force_n,5):
                # one rare role guaranteed; already strong natural role is not downgraded
                j=choice(force_dist)
                if role_rare_rank(j)>role_rare_rank(role):role=j
            if upper_force_n>5 and upper_init_index<min(upper_force_n-5,5):
                forced=STRONG if np.random.random()<.7 else SC
                if role_rare_rank(forced)>role_rare_rank(role):role=forced
            if role==special_streak_role:special_streak_n+=1
            else:special_streak_role=role;special_streak_n=1
            b,premium=upper_bonus(role,special_streak_n,amt,pct,n,prem,pstrong,pchance,role_scales)
            if premium:two_thousand+=1
            prize=min(2000,100+b)
            balance+=prize
            upper_initial_payout+=prize
            total_awarded_bonus+=prize
            upper_init_index+=1
            if upper_init_index>=5:
                mean_upper_initial+=upper_initial_payout
                initial_count[1]+=1
                stage=UPPER_AT;streak_role=-1;streak_n=0;free_replay=False;upper_force_n=0
                special_streak_n=0;special_streak_role=-1
        else:
            raise ValueError('invalid stage')
    paid=int(games.sum())
    return (inn,out,games,special,initial_count,cz_counts,bar_counts,two_thousand,upper_rarewrites,
            at_starts,lower_entries,upper_entries,completed_at,sum_at,sum_at_paid,max_at,at_net_samples,
            single_hits,linked_hits,extra_hits,mean_lower_initial,mean_upper_initial,total_awarded_bonus,paid,
            comeback_upper_returns,comeback_lower_returns,comeback_attempts_upper,comeback_attempts_lower,
            normal_non_comeback_games,normal_non_comeback_hits,comeback_free_games,comeback_paid_games,
            upper_episodes,upper_completed,upper_gain_sum,upper_source_count,upper_source_gain,upper_reloops,
            upper_samples,upper_entry_net_games)

def validate(d):
    tests=[]
    def chk(cond,msg):
        tests.append({'check':msg,'pass':bool(cond)})
        if not cond:raise AssertionError(msg)
    p=make_params(d)
    chk(np.allclose(p[0][:,-1],1),'Role draws exclusive / sum=1 each stage')
    chk(all(abs(np.sum(p[1][k,j])-1)<1e-9 for k in range(4) for j in range(6)),'Each mode up distribution sums=100%')
    chk(all(abs(np.sum(p[4][j])-1)<1e-9 for j in range(6)),'AT exit mode distribution sums=100%')
    chk(all(abs(np.sum(p[3][j])-1)<1e-9 for j in range(6) if p[2][j]>0),'Mode drop distribution sums=100%')
    chk(abs(1-(1-p[20])**5-0.5)<1e-8,'5G cumulative CZ exactly 50%')
    chk(abs(1-(1-p[21])**32-0.2)<1e-8,'32G extra comeback cumulative exactly 20%')
    chk(d['fixed_requirements']['special_stage']['bet']==0 and d['fixed_requirements']['special_stage']['payout_all_roles']==0,'Free phases 0 bet 0 payout')
    chk(d['fixed_requirements']['upper_max_single_role_bonus_medals']==2000,'Upper single-role cap 2000')
    chk(d['fixed_requirements']['upper_initial_guarantee_total']==500,'Upper 500 guarantee')
    return tests

def main():
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument("--frames",type=int,default=50000000)
    parser.add_argument("--replicates",type=int,default=2)
    parser.add_argument("--seed",type=int,default=20261109)
    parser.add_argument("--output",default="runtime-evidence/HYPER_DOG_V6_UPPER_ENTRY_AUDIT/result.json")
    args=parser.parse_args()
    data=json.loads(DATA.read_text())
    validate(data)
    p=make_params(data)
    full=[]
    for setting in range(1,7):
        all_samples=[]
        total_frames=0;total_normal=0;at_starts=0
        count=0;sum_net=0;source_counts=np.zeros(4,dtype=np.int64)
        source_nets=np.zeros(4,dtype=np.int64);loops=0;upper_g=0
        for rep in range(args.replicates):
            seed=args.seed+setting*100021+rep*2339
            x=simulate(setting,args.frames,seed,p)
            # Upper audit append positions:
            e,n,gain,src_counts,src_gain,reloops,samples,upper_net_games=x[32:40]
            total_frames+=int(np.sum(x[2]))
            total_normal+=int(x[2][0])
            at_starts+=int(x[9])
            count+=int(n);sum_net+=int(gain)
            source_counts+=src_counts;source_nets+=src_gain
            loops+=int(reloops);upper_g+=int(upper_net_games)
            all_samples.extend(samples[:int(min(n,len(samples)))].tolist())
            print("UPPER_AUDIT_SAMPLE",json.dumps({
                "setting":setting,"seed":seed,"frames":int(np.sum(x[2])),
                "initial_AT_starts":int(x[9]),"upper_episodes":int(e),
                "upper_ended":int(n),"upper_net":int(gain),
                "upper_source_counts":src_counts.tolist()
            }),flush=True)
        sm=np.array(all_samples,dtype=np.int64)
        assert count>100 and len(sm)==count and sum(source_counts)==count
        assert int(np.sum(source_nets))==sum_net
        mean=sum_net/count
        sources=["NORMAL_BAR","LOWER_BAR","LOWER_CZ_SUCCESS","UPPER_COMEBACK"]
        row={
          "setting":setting,"total_frames":total_frames,
          "normal_frames":total_normal,"initial_AT_starts":at_starts,
          "upper_episode_count":count,
          "upper_entry_per_all_normal_frames_1_in":total_normal/count,
          "upper_entry_per_all_frames_1_in":total_frames/count,
          "upper_entry_per_initial_AT_start":count/at_starts,
          "upper_session_net_expected_medals":mean,
          "upper_session_net_median_medals":float(np.median(sm)),
          "upper_session_net_percentiles":{str(k):float(np.percentile(sm,k)) for k in (10,25,50,75,90,95,99)},
          "upper_session_net_ge_1000":float(np.mean(sm>=1000)),
          "upper_session_net_ge_3000":float(np.mean(sm>=3000)),
          "upper_session_net_ge_5000":float(np.mean(sm>=5000)),
          "upper_session_net_ge_10000":float(np.mean(sm>=10000)),
          "upper_session_net_max":int(np.max(sm)),
          "upper_successful_CZ_reloops":loops,
          "mean_upper_initial_rounds_per_episode":(count+loops)/count,
          "mean_upper_paid_games_per_episode":upper_g/count,
          "origin":{s:{"starts":int(source_counts[i]),"share":float(source_counts[i]/count),
            "mean_net_medals":float(source_nets[i]/source_counts[i]) if source_counts[i]>0 else None}
            for i,s in enumerate(sources)}
        }
        full.append(row)
        print("UPPER_AUDIT_SETTING",json.dumps(row,ensure_ascii=False),flush=True)
    out=pathlib.Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({"definition":"UPPER_INITIAL first entry to UPPER_CZ fail; successful upper CZ repeats retained, comeback after CZ failure opens new episode; upper paid stage payout minus effective bet, excludes earlier lower AT and normal games","settings":full},ensure_ascii=False,indent=2)+"\n")
    print("UPPER_AUDIT_COMPLETE",out,flush=True)

if __name__=="__main__":main()
