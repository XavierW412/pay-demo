import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/',
  timeout: 10_000,
  withCredentials: true,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})
