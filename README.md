## PhotoLab Output Settings Reorderer

This is a quick attempt at a graphical application to **reorder DxO PhotoLab's export settings list**.

Latest beta release (Windows .msi): https://github.com/rschr/photolab-output-settings-reorderer/releases/tag/0.4.0  

<img src="https://github.com/user-attachments/assets/411c2c7f-2ce5-43b6-8999-b62bdeb8a3e5" alt="img1" width="200" />

<img src="https://github.com/user-attachments/assets/ce1b0cfb-6cac-4846-aee4-392c6f93334e" alt="img1" width="200" />

<img src="https://github.com/user-attachments/assets/3eb9eb05-039b-433d-88e2-00b7db74ecfa" alt="img1" width="200" />

<img src="https://github.com/user-attachments/assets/7d5243ce-b565-404e-865a-97ce594b7de9" alt="img1" width="227" />

<img src="https://github.com/user-attachments/assets/0d2cbce8-1a0c-41e5-8e84-764109eca3fd" alt="img1" width="227" />



Opens a user config file from the list of available ones. You’ll then see the settings listed in their current order. You can reorder 
any entry by clicking on it and using the up/down buttons. If you want, you can also edit the names of the individual settings. When 
you’re done, click Save, and the changes will be saved in PhotoLab. Just in case, the original config is kept as a backup with a 
timestamp and can be restored as needed, either using the application itself or manually.

A view of every named setting's full parameters-and-values list as in DxO's config file can also be opened.

The Settings Reorderer should work with virtually all PhotoLab versions starting with 3. (However, full cross-version compatibility 
of the user.config files it reads cannot be assumed.)

The Reorderer can also **import export settings from other (like, earlier) versions**. PhotoLab keeps settings from earlier versions
on the system. This should come in handy when, like I've been experiencing it, a new PhotoLab Version does not import the last version's
settings.

UI languages implemented so far are English and German.

The application is written – with massive help of Claude Code – in Java with JavaFx, packaging is implemented for Windows (.msi and .exe installers) 
and Linux (.deb packaging)[^1]. Apple macOS packaging is easy to add, but since I have no Apple myself I didn't do that yet.

Disclaimer: this is beta software, no warranties that it doesn't break things.

In first tests with PhotoLab 9.5 and user configs dating back to PhotoLab 3 though it did what it's supposed to do.

[^1]: Only for development purposes as PhotoLab unfortunately is not available for Linux, doesn't run on Wine, and 
even a VM is only of use if it's got access to its own dedicated GPU
