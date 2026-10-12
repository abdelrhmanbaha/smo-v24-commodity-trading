# How to Create the Repo and Push This

1. On GitHub: **New repository** → give it a name, e.g. `smo-variant24-commodity-trading` →
   Public is fine (makes it easy to hand the instructor a link) → Create repository
   (leave "Add README" off, so there's no conflict with the README in this folder).

2. On your computer, in the folder with these files (`README.md`, three `.mmd`, three `.png`):

   ```bash
   git init
   git add README.md SETUP.md class-diagram.mmd class-diagram.png sequence-arrival.mmd sequence-arrival.png sequence-device-free.mmd sequence-device-free.png
   git commit -m "Add class diagram and sequence diagrams for variant 24"
   git branch -M main
   git remote add origin https://github.com/<your-username>/smo-variant24-commodity-trading.git
   git push -u origin main
   ```

3. Open the repo on GitHub and confirm the `README.md` renders all three diagrams right on
   the page (GitHub renders ```mermaid``` code blocks natively, no plugin needed).

4. Submit the repo link (or a direct link to `README.md`) as your artifact for the 16.10
   deadline.

If you later add the simulator's code to this same repo, just create a `src/` folder next to
these files and commit there as usual — the diagrams and README can be updated as the
implementation evolves.
