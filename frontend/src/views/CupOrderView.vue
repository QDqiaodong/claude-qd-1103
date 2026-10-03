<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const recipes = ref([])
const members = ref([])
const dialogVisible = ref(false)
const filter = reactive({ status: '' })

const payTypes = ['微信', '支付宝', '现金']
const statuses = ['已出杯', '已退款']

const draft = reactive({ memberId: null, payType: '微信', items: [] })

const recipeMap = computed(() => {
  const m = {}
  recipes.value.forEach(r => { m[r.id] = r.name })
  return m
})
const memberMap = computed(() => {
  const m = { null: '散客' }
  members.value.forEach(x => { m[x.id] = x.name })
  return m
})

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
  return items.map(it => `${recipeMap.value[it.recipeId] || ('配方#' + it.recipeId)}×${it.qty}`).join('，')
}

function openCreate() {
  draft.memberId = null
  draft.payType = '微信'
  draft.items = []
  dialogVisible.value = true
}

function addItem() {
  draft.items.push({ recipeId: recipes.value.length ? recipes.value[0].id : null, qty: 1 })
}

function removeItem(idx) {
  draft.items.splice(idx, 1)
}

async function submit() {
  try {
    if (!draft.items.length) {
      ElMessage.warning('请至少添加一种配方')
      return
    }
    const payload = {
      memberId: draft.memberId,
      payType: draft.payType,
      items: draft.items.map(it => ({ recipeId: it.recipeId, qty: it.qty }))
    }
    await api.createCupOrder(payload)
    ElMessage.success('已生成出杯单')
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function refund(row) {
  try {
    await api.refundCupOrder(row.id)
    ElMessage.success('已退款')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
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

    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="单号" width="80" />
      <el-table-column label="会员" width="110">
        <template #default="{ row }">{{ memberMap[row.memberId] || '散客' }}</template>
      </el-table-column>
      <el-table-column prop="payType" label="支付" width="100" />
      <el-table-column label="配方明细">
        <template #default="{ row }">{{ itemsText(row.items) }}</template>
      </el-table-column>
      <el-table-column prop="total" label="金额" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '已出杯' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="row.status === '已出杯'" link type="danger" @click="refund(row)">退款</el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="生成出杯单" width="560px">
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确认出杯</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filters { display: flex; gap: 10px; }
.items { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; align-items: center; gap: 10px; }
</style>
