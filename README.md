# dootask workbench 备份分支

本分支只保存一个 git bundle，内容为 dootask 基线 `6f111cd97`（v1.9.66）之上的全部工作台提交。
它不能替代仓库镜像，只用于在无法直接推送完整历史时保留一份异地副本。

## 恢复

```bash
git clone https://github.com/kuaifan/dootask.git
cd dootask
git checkout 6f111cd97
git bundle verify /path/to/dootask-workbench.bundle
git fetch /path/to/dootask-workbench.bundle 'refs/heads/*:refs/remotes/bundle/*'
git branch workbench-planning bundle/workbench-planning
```
