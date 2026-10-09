import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/',
  timeout: 10_000,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})
