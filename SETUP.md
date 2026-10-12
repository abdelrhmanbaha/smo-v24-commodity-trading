# Как создать репозиторий и запушить это на GitHub

1. На GitHub: **New repository** → имя, например `smo-variant24-commodity-trading` →
   можно сразу Public (так проще дать ссылку преподавателю) → Create repository
   (без README/.gitignore, чтобы не было конфликтов при первом push).

2. На своём компьютере, в папке с файлами из этой поставки (`README.md`, три `.mmd` и три `.png`):

   ```bash
   git init
   git add README.md class-diagram.mmd class-diagram.png sequence-arrival.mmd sequence-arrival.png sequence-device-free.mmd sequence-device-free.png
   git commit -m "Add class diagram and sequence diagrams for variant 24"
   git branch -M main
   git remote add origin https://github.com/<ваш-логин>/smo-variant24-commodity-trading.git
   git push -u origin main
   ```

3. Откройте репозиторий на GitHub и проверьте, что `README.md` отрисовывает все три диаграммы
   прямо на странице (блоки ```mermaid``` GitHub рендерит сам, без плагинов).

4. Ссылку на репозиторий (или прямую ссылку на `README.md`) отправляете как артефакт
   к дедлайну 16.10.

Если позже добавите код симулятора в этот же репозиторий — просто создайте папку `src/`
рядом и коммитьте туда как обычно; диаграммы и README можно будет обновлять по мере того,
как меняется реализация.
