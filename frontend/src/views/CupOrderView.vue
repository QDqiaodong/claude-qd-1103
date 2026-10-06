<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const recipes = ref([])
const members = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const refundingId = ref(null)
const filter = reactive({ status: '' })

const payTypes = ['微信', '支付宝', '现金']
const statuses = ['已出杯', '已退款']

const draft = reactive({ memberId: null, payType: '微信', items: [] })

// 预计扣减预览（仅页面提示用，提交仍以服务端事务内最新库存为准）
const preview = ref(null)
const previewLoading = ref(false)
let previewTimer = null

const recipeMap = computed(() => {
  const m = {}
  recipes.value.forEach(r => { m[r.id] = r })
  return m
})
const memberMap = computed(() => {
  const m = {}
  members.value.forEach(x => { m[x.id] = x })
  return m
})

const previewBlocked = computed(() => !!preview.value && preview.value.blocked)

async function load() {
  const [ords, recs, mems] = await Promise.all([
    api.listCupOrders(filter.status || undefined),
    api.listRecipes(),
    api.listMembers()
  ])
  list.value = ords
  recipes.value = recs
  members.value = mems
}

function itemsText(items) {
  if (!items || !items.length) return '—'
  return items.map(it => `${recipeMap.value[it.recipeId]?.name || ('配方#' + it.recipeId)}×${it.qty}`).join('，')
}

function memberName(memberId) {
  if (memberId == null) return '散客'
  return memberMap.value[memberId]?.name || `会员#${memberId}`
}

function deductionText(d) {
  return `${d.ingredientName} ${d.qty}${d.unit}`
}

function openCreate() {
  draft.memberId = null
  draft.payType = '微信'
  draft.items = []
  preview.value = null
  dialogVisible.value = true
}

function addItem() {
  draft.items.push({ recipeId: recipes.value.length ? recipes.value[0].id : null, qty: 1 })
}

function removeItem(idx) {
  draft.items.splice(idx, 1)
}

// 明细 / 会员变化后防抖拉取最新预计扣减
watch(() => [JSON.stringify(draft.items), draft.memberId, draft.payType], () => {
  if (!dialogVisible.value) return
  clearTimeout(previewTimer)
  previewTimer = setTimeout(fetchPreview, 250)
})

async function fetchPreview() {
  if (!draft.items.length) {
    preview.value = null
    return
  }
  if (draft.items.some(it => !it.recipeId || !it.qty || it.qty <= 0)) {
    preview.value = null
    return
  }
  previewLoading.value = true
  try {
    preview.value = await api.previewCupOrder({
      memberId: draft.memberId,
      payType: draft.payType,
      items: draft.items.map(it => ({ recipeId: it.recipeId, qty: it.qty }))
    })
  } catch (e) {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

async function submit() {
  if (!draft.items.length) {
    ElMessage.warning('请至少添加一种配方')
    return
  }
  // 页面先拦一道；真正的裁判是服务端
  if (previewBlocked.value) {
    ElMessage.error(preview.value.problems.join('；'))
    return
  }
  submitting.value = true
  try {
    const payload = {
      memberId: draft.memberId,
      payType: draft.payType,
      items: draft.items.map(it => ({ recipeId: it.recipeId, qty: it.qty }))
    }
    await api.createCupOrder(payload)
    ElMessage.success('已生成出杯单，原料库存已扣减')
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function refund(row) {
  if (refundingId.value) return
  refundingId.value = row.id
  try {
    const res = await api.refundCupOrder(row.id)
    const tips = []
    tips.push(res.restoreResult === '已回补' ? '库存已按扣减快照回补' : '库存未回补')
    if (res.pointsEarned > 0) tips.push(`积分已回退 ${res.pointsEarned}`)
    if (res.remark) tips.push(res.remark)
    ElMessage.success('已退款：' + tips.join('，'))
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    refundingId.value = null
  }
}

const emit = defineEmits(['changed'])

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <div class="filters">
        <el-select v-model="filter.status" placeholder="状态" clearable @change="load" style="width:160px">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate">生成出杯单</el-button>
    </div>

    <el-table :data="list" border stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="ledger">
            <div class="ledger-title">原料扣减快照（实际履约台账，配方后续编辑不改变本单消耗）</div>
            <div v-if="row.deductions && row.deductions.length" class="ledger-lines">
              <el-tag
                v-for="d in row.deductions"
                :key="d.id"
                size="small"
                :type="row.status === '已退款' && d.restored === 1 ? 'success' : 'warning'"
                class="ledger-tag"
              >
                {{ deductionText(d) }}
                <span v-if="row.status === '已退款'">（{{ d.restored === 1 ? '已回补' : '未回补' }}）</span>
              </el-tag>
            </div>
            <div v-else class="ledger-empty">历史旧单：无原料扣减快照，退款不回补库存</div>
            <div v-if="row.remark" class="ledger-remark">备注：{{ row.remark }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="单号" width="80" />
      <el-table-column label="会员" width="110">
        <template #default="{ row }">{{ memberName(row.memberId) }}</template>
      </el-table-column>
      <el-table-column prop="payType" label="支付" width="90" />
      <el-table-column label="配方明细">
        <template #default="{ row }">{{ itemsText(row.items) }}</template>
      </el-table-column>
      <el-table-column prop="total" label="金额" width="90" />
      <el-table-column label="本次积分" width="90">
        <template #default="{ row }">{{ row.pointsEarned || 0 }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === '已出杯' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="库存履约" width="110">
        <template #default="{ row }">
          <el-tag :type="row.stockResult === '已扣减' ? 'success' : 'info'" size="small">
            {{ row.stockResult || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退款回补" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === '已退款'" :type="row.restoreResult === '已回补' ? 'success' : 'danger'" size="small">
            {{ row.restoreResult || '—' }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === '已出杯'"
            link
            type="danger"
            :loading="refundingId === row.id"
            @click="refund(row)"
          >退款</el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="生成出杯单" width="640px">
      <el-form label-width="80px">
        <el-form-item label="会员">
          <el-select v-model="draft.memberId" clearable placeholder="散客（不选）" style="width:100%">
            <el-option v-for="m in members" :key="m.id" :label="m.name + '（' + m.phone + '）'" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付">
          <el-radio-group v-model="draft.payType">
            <el-radio v-for="p in payTypes" :key="p" :value="p">{{ p }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="配方明细">
          <div class="items">
            <div v-for="(it, idx) in draft.items" :key="idx" class="item-row">
              <el-select v-model="it.recipeId" style="width:240px">
                <el-option v-for="r in recipes" :key="r.id" :label="r.name + '（¥' + r.price + '）'" :value="r.id" />
              </el-select>
              <el-input-number v-model="it.qty" :min="1" />
              <el-button link type="danger" @click="removeItem(idx)">删</el-button>
            </div>
            <el-button link type="primary" @click="addItem">+ 添加配方</el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="draft.items.length" label="预计扣减">
          <div v-loading="previewLoading" class="preview">
            <template v-if="preview">
              <div class="preview-summary">
                <span>预计金额：<b>¥{{ preview.total }}</b></span>
                <span v-if="preview.pointsEarned > 0">预计积分：<b>+{{ preview.pointsEarned }}</b></span>
              </div>
              <el-table :data="preview.lines" size="small" border>
                <el-table-column label="原料" prop="ingredientName" width="110" />
                <el-table-column label="需求" width="110">
                  <template #default="{ row }">{{ row.need }} {{ row.unit }}</template>
                </el-table-column>
                <el-table-column label="当前库存" width="110">
                  <template #default="{ row }">{{ row.stock }} {{ row.unit }}</template>
                </el-table-column>
                <el-table-column label="判定">
                  <template #default="{ row }">
                    <el-tag v-if="row.available" type="success" size="small">充足</el-tag>
                    <el-tag v-else type="danger" size="small">{{ row.problem }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-alert
                v-if="preview.blocked"
                type="error"
                :closable="false"
                show-icon
                class="preview-alert"
                title="存在不足 / 停用 / 失效原料，整单无法出杯（服务端将拒绝并保持原库存、原积分）"
                :description="preview.problems.join('；')"
              />
              <div v-else class="preview-hint">预计扣减仅供参考，提交时以服务端最新库存为准。</div>
            </template>
            <div v-else class="preview-hint">请选择配方并填写合法杯数。</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="previewLoading || previewBlocked"
          @click="submit"
        >确认出杯</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filters { display: flex; gap: 10px; }
.items { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; align-items: center; gap: 10px; }
.preview { width: 100%; }
.preview-summary { display: flex; gap: 20px; margin-bottom: 8px; font-size: 13px; }
.preview-alert { margin-top: 8px; }
.preview-hint { margin-top: 6px; font-size: 12px; color: var(--muted, #999); }
.ledger { padding: 10px 18px; }
.ledger-title { font-size: 13px; color: var(--muted, #999); margin-bottom: 8px; }
.ledger-lines { display: flex; flex-wrap: wrap; gap: 8px; }
.ledger-tag { white-space: normal; height: auto; padding: 4px 10px; }
.ledger-empty { font-size: 13px; color: #909399; }
.ledger-remark { margin-top: 8px; font-size: 12px; color: #e6a23c; }
</style>
