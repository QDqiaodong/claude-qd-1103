<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const dialogVisible = ref(false)
const editing = ref(false)
const form = reactive({ id: null, phone: '', name: '', points: 0, level: '普通' })
const filter = reactive({ level: '' })

const levels = ['普通', '银卡', '金卡']

async function load() {
  list.value = await api.listMembers(filter.level || undefined)
}

function resetForm() {
  form.id = null
  form.phone = ''
  form.name = ''
  form.points = 0
  form.level = '普通'
}

function openCreate() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editing.value) {
      await api.updateMember(form.id, { name: form.name, points: form.points, level: form.level })
      ElMessage.success('已更新会员')
    } else {
      await api.createMember({ ...form })
      ElMessage.success('已新增会员')
    }
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function adjustPoints(row) {
  try {
    const { value } = await ElMessageBox.prompt('输入积分变动（正为充值，负为扣减）', '调整积分 · ' + row.name, {
      inputPattern: /^-?\d+$/,
      inputErrorMessage: '请输入整数'
    })
    await api.addMemberPoints(row.id, parseInt(value, 10))
    ElMessage.success('积分已更新')
    await load()
    emit('changed')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

const emit = defineEmits(['changed'])

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <div class="filters">
        <el-select v-model="filter.level" placeholder="等级" clearable @change="load" style="width:160px">
          <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate">新增会员</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="name" label="昵称" />
      <el-table-column prop="points" label="积分" width="90" sortable />
      <el-table-column prop="level" label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.level === '金卡' ? 'warning' : (row.level === '银卡' ? 'success' : 'info')">
            {{ row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="adjustPoints(row)">调积分</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑会员' : '新增会员'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" :disabled="editing" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="积分">
          <el-input-number v-model="form.points" :min="0" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level" style="width:100%">
            <el-option v-for="l in levels" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.filters { display: flex; gap: 10px; }
</style>
